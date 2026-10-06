package com.locatedo.locatedo.core.sync

import com.connectrpc.Code
import com.locatedo.household.v1.Plan
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.FakeProStatus
import com.locatedo.locatedo.core.data.RoomSyncStateRepository
import com.locatedo.locatedo.core.data.asEntity
import com.locatedo.locatedo.core.data.category
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.todo
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PendingWriteEntity
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.FakeSyncService
import com.locatedo.locatedo.testing.FakeWriteServices
import com.locatedo.locatedo.testing.InMemorySessionStore
import com.locatedo.locatedo.testing.testSession
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private lateinit var database: LocateDoDatabase
    private val services = FakeWriteServices()
    private val pulls = FakeSyncService()
    private val account = FakeAccountService()
    private val proStatus = FakeProStatus()
    private val householdId = UUID.fromString("0199bd00-0000-7000-8000-0000000000aa")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sendsTheQueueInOrderWithTheHouseholdId() = runTest {
        val store = place("Store")
        val milk = todo("Milk", store.id)
        val gym = category("Gym")
        enqueue(Write.put(gym), Write.put(store), Write.put(milk), Write.completion(milk.id, fixedNow), Write.deleteTodo(milk.id))
        val fixture = fixture()

        fixture.engine.drain()

        assertEquals(listOf("putCategory", "putPlace", "putTodo", "setTodoCompletion", "deleteTodo"), services.sent)
        assertEquals(List(3) { householdId.toString() }, services.householdIds)
        assertEquals(0, queueCount())
    }

    @Test
    fun doesNothingWhenSignedOut() = runTest {
        enqueue(Write.put(place("Store")))
        val fixture = fixture(signedIn = false)

        fixture.engine.drain()

        assertTrue(services.sent.isEmpty())
        assertEquals(1, queueCount())
    }

    @Test
    fun waitsForTheHousehold() = runTest {
        enqueue(Write.put(place("Store")))
        val fixture = fixture(householdId = null)

        fixture.engine.drain()

        assertTrue(services.sent.isEmpty())
        assertEquals(1, queueCount())
    }

    @Test
    fun anUnavailableServerKeepsTheHeadAndStops() = runTest { keepsTheHeadAndStops(Code.UNAVAILABLE) }

    @Test
    fun aDeniedWriteKeepsTheHeadAndStops() = runTest { keepsTheHeadAndStops(Code.PERMISSION_DENIED) }

    @Test
    fun anInternalErrorKeepsTheHeadAndStops() = runTest { keepsTheHeadAndStops(Code.INTERNAL_ERROR) }

    @Test
    fun anExistingRowDropsTheHeadAndContinues() = runTest { dropsTheHeadAndContinues(Code.ALREADY_EXISTS) }

    @Test
    fun anInvalidWriteDropsTheHeadAndContinues() = runTest { dropsTheHeadAndContinues(Code.INVALID_ARGUMENT) }

    @Test
    fun aMissingRowDropsTheHeadAndContinues() = runTest { dropsTheHeadAndContinues(Code.NOT_FOUND) }

    @Test
    fun overlappingDrainsSendEachWriteOnce() = runTest {
        val store = place("Store")
        enqueue(Write.put(store), Write.deletePlace(store.id))
        val fixture = fixture()

        val first = launch { fixture.engine.drain() }
        val second = launch { fixture.engine.drain() }
        first.join()
        second.join()

        assertEquals(listOf("putPlace", "deletePlace"), services.sent)
        assertEquals(0, queueCount())
    }

    @Test
    fun rescheduledDrainReplacesThePendingOne() = runTest {
        val store = place("Store")
        enqueue(Write.put(store))
        val fixture = fixture()

        val first = fixture.engine.scheduleDrain(delayMillis = 60_000)
        enqueue(Write.deletePlace(store.id))
        val second = fixture.engine.scheduleDrain(delayMillis = 0)
        first.join()
        second.join()

        assertTrue(first.isCancelled)
        assertEquals(listOf("putPlace", "deletePlace"), services.sent)
        assertEquals(0, queueCount())
    }

    @Test
    fun syncSendsTheQueueThenPullsEveryPage() = runTest {
        val fixture = fixture()
        fixture.syncState.set(SyncState(householdId = householdId, cursor = 10))
        enqueue(Write.put(place("Local")))
        val placeId = uuidV7()
        pulls.respond(
            pullPage(listOf(placeChange(placeId, version = 11)), cursor = 11, hasMore = true),
            pullPage(listOf(todoChange(uuidV7(), placeId, version = 12)), cursor = 12, plan = Plan.PLAN_PRO),
        )

        fixture.engine.sync()

        assertEquals(listOf("putPlace"), services.sent)
        assertEquals(listOf(10L, 11L), pulls.cursors)
        assertTrue(pulls.householdIds.all { it == householdId.toString() })
        val state = fixture.syncState.get()
        assertEquals(12L, state.cursor)
        assertEquals(com.locatedo.locatedo.core.model.Plan.PRO, state.plan)
        val pulled = database.placeDao().observeWithTodos(placeId.toString()).first()
        assertEquals(1, pulled?.todos?.size)
    }

    @Test
    fun resetOnAnyPageReplacesLocalData() = runTest {
        val fixture = fixture()
        database.placeDao().upsert(place("Local").asEntity())
        pulls.respond(pullPage(emptyList(), cursor = 30, reset = true))

        fixture.engine.sync()

        assertEquals(0, database.placeDao().count())
        assertEquals(30L, fixture.syncState.get().cursor)
    }

    @Test
    fun failedPullKeepsTheCursorAndAppliesNothing() = runTest {
        val fixture = fixture()
        pulls.respond(pullPage(listOf(placeChange(uuidV7(), version = 1)), cursor = 1, hasMore = true))
        pulls.fail(afterPages = 1)

        fixture.engine.sync()

        assertEquals(listOf(0L, 1L), pulls.cursors)
        assertEquals(0L, fixture.syncState.get().cursor)
        assertEquals(0, database.placeDao().count())
    }

    @Test
    fun drainAloneDoesNotPull() = runTest {
        val fixture = fixture()

        fixture.engine.drain()

        assertTrue(pulls.cursors.isEmpty())
    }

    @Test
    fun pullWaitsForTheHousehold() = runTest {
        val fixture = fixture(householdId = null)

        fixture.engine.sync()

        assertTrue(pulls.cursors.isEmpty())
    }

    @Test
    fun aSyncRequestedDuringADrainStillPulls() = runTest {
        enqueue(Write.put(place("Store")))
        val fixture = fixture()

        val drained = launch { fixture.engine.drain() }
        val synced = launch { fixture.engine.sync() }
        drained.join()
        synced.join()

        assertEquals(listOf("putPlace"), services.sent)
        assertEquals(listOf(0L), pulls.cursors)
    }

    @Test
    fun aDeniedWriteWithARejectedRefreshEndsTheSession() = runTest {
        enqueue(Write.put(place("Store")))
        val fixture = fixture()
        services.fail(Code.PERMISSION_DENIED)
        account.refreshFailure = Code.UNAUTHENTICATED
        val ended = count(fixture.authenticator.sessionEnded)
        val removed = count(fixture.engine.removed)

        fixture.engine.drain()

        assertEquals(1, account.refreshes.size)
        assertEquals(1, ended.value)
        assertEquals(0, removed.value)
        assertNull(fixture.authenticator.current())
    }

    @Test
    fun aDeniedPullWithAValidSessionIsARemoval() = runTest {
        val fixture = fixture()
        pulls.fail(afterPages = 0, code = Code.PERMISSION_DENIED)
        val removed = count(fixture.engine.removed)

        fixture.engine.sync()

        assertEquals(1, account.refreshes.size)
        assertEquals(1, removed.value)
        assertNotNull(fixture.authenticator.current())
    }

    @Test
    fun otherFailuresDoNotTouchTheSession() = runTest {
        val fixture = fixture()
        pulls.fail(afterPages = 0)

        fixture.engine.sync()

        assertTrue(account.refreshes.isEmpty())
    }

    private suspend fun TestScope.keepsTheHeadAndStops(code: Code) {
        val store = place("Store")
        enqueue(Write.put(store), Write.deletePlace(store.id))
        val fixture = fixture()
        services.fail(code)

        fixture.engine.drain()

        assertEquals(listOf("putPlace"), services.sent)
        val head = checkNotNull(database.pendingWriteDao().head())
        assertEquals("putPlace", head.kind)
        assertEquals(1, head.attempts)
        assertEquals(2, queueCount())

        fixture.engine.drain()

        assertEquals(listOf("putPlace", "putPlace", "deletePlace"), services.sent)
        assertEquals(0, queueCount())
    }

    private suspend fun TestScope.dropsTheHeadAndContinues(code: Code) {
        val store = place("Store")
        enqueue(Write.put(store), Write.deletePlace(store.id))
        val fixture = fixture()
        services.fail(code)

        fixture.engine.drain()

        assertEquals(listOf("putPlace", "deletePlace"), services.sent)
        assertEquals(0, queueCount())
    }

    private suspend fun enqueue(vararg writes: Write) {
        for (write in writes) {
            database.pendingWriteDao().insert(PendingWriteEntity(kind = write.kind.key, payload = write.serialized(), createdAt = 0))
        }
    }

    private suspend fun queueCount(): Int = database.pendingWriteDao().count()

    private fun <T> TestScope.count(flow: kotlinx.coroutines.flow.Flow<T>): Counter {
        val counter = Counter()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { counter.value += 1 } }
        return counter
    }

    private suspend fun TestScope.fixture(signedIn: Boolean = true, householdId: UUID? = this@SyncEngineTest.householdId): Fixture {
        val store = InMemorySessionStore(if (signedIn) testSession else null)
        val authenticator = Authenticator(store, account, AccessTokenStore(), fixedClock, this)
        val syncState = RoomSyncStateRepository(database.syncStateDao())
        syncState.set(SyncState(householdId = householdId))
        val engine = DefaultSyncEngine(
            sender = WriteSender(services, services, services, authenticator),
            syncService = pulls,
            authenticator = authenticator,
            database = database,
            pendingWrites = database.pendingWriteDao(),
            queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock),
            syncState = syncState,
            applier = ChangeApplier(database.categoryDao(), database.placeDao(), database.todoDao(), database.membershipDao()),
            limitRejection = LimitRejection(database.placeDao(), database.todoDao(), fixedClock),
            proStatus = proStatus,
            scope = this,
        )
        return Fixture(engine, authenticator, syncState)
    }

    class Fixture(val engine: DefaultSyncEngine, val authenticator: Authenticator, val syncState: RoomSyncStateRepository)

    class Counter(var value: Int = 0)
}
