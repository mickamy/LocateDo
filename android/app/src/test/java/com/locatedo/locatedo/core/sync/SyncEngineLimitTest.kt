package com.locatedo.locatedo.core.sync

import com.connectrpc.Code
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.appstatus.MaintenanceGate
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.data.FakeProStatus
import com.locatedo.locatedo.core.data.RoomSyncStateRepository
import com.locatedo.locatedo.core.data.asEntity
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.todo
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.database.PendingWriteEntity
import com.locatedo.locatedo.core.model.FreeLimit
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncEngineLimitTest {
    private lateinit var database: LocateDoDatabase
    private val services = FakeWriteServices()
    private val proStatus = FakeProStatus()
    private val limits = mutableListOf<FreeLimit>()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aWriteOverTheFreeLimitIsDroppedAndUndoneWithoutPro() = runTest {
        val store = place("Store")
        val office = place("Office")
        database.placeDao().upsert(store.asEntity())
        database.todoDao().upsert(todo("Milk", store.id).asEntity())
        database.placeDao().upsert(office.asEntity())
        enqueue(Write.put(store), Write.put(office))
        services.fail(Code.FAILED_PRECONDITION)
        val engine = engine()

        engine.drain()

        assertEquals(listOf("putPlace", "putPlace"), services.sent)
        assertEquals(listOf("Office"), database.placeDao().observeAll().first().map { it.name })
        assertTrue(database.todoDao().observeAll().first().isEmpty())
        assertEquals(0, database.pendingWriteDao().count())
        assertEquals(listOf(FreeLimit.PLACES), limits)
    }

    @Test
    fun aRejectedReopenIsCompletedAgain() = runTest {
        val store = place("Store")
        val milk = todo("Milk", store.id)
        database.placeDao().upsert(store.asEntity())
        database.todoDao().upsert(milk.asEntity())
        enqueue(Write.completion(milk.id, null))
        services.fail(Code.FAILED_PRECONDITION)
        val engine = engine()

        engine.drain()

        assertEquals(fixedNow.toEpochMilli(), database.todoDao().get(milk.id.toString())?.completedAt)
        assertEquals(0, database.pendingWriteDao().count())
        assertEquals(listOf(FreeLimit.OPEN_TODOS), limits)
    }

    @Test
    fun aWriteOverTheFreeLimitWaitsForTheWebhookWithPro() = runTest {
        val store = place("Store")
        database.placeDao().upsert(store.asEntity())
        enqueue(Write.put(store))
        services.fail(Code.FAILED_PRECONDITION)
        proStatus.pro = true
        val engine = engine()

        engine.drain()

        assertEquals(1, database.pendingWriteDao().count())
        assertEquals(1, database.placeDao().count())
        assertTrue(limits.isEmpty())
    }

    private suspend fun enqueue(vararg writes: Write) {
        for (write in writes) {
            database.pendingWriteDao().insert(PendingWriteEntity(kind = write.kind.key, payload = write.serialized(), createdAt = 0))
        }
    }

    private suspend fun TestScope.engine(): DefaultSyncEngine {
        val authenticator = Authenticator(InMemorySessionStore(testSession), FakeAccountService(), AccessTokenStore(), fixedClock, this)
        val syncState = RoomSyncStateRepository(database.syncStateDao())
        syncState.set(SyncState(householdId = UUID.fromString("0199bd00-0000-7000-8000-0000000000aa")))
        val engine = DefaultSyncEngine(
            sender = WriteSender(services, services, services, authenticator),
            syncService = FakeSyncService(),
            authenticator = authenticator,
            database = database,
            pendingWrites = database.pendingWriteDao(),
            queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock),
            syncState = syncState,
            applier = ChangeApplier(database.categoryDao(), database.placeDao(), database.todoDao(), database.membershipDao()),
            limitRejection = LimitRejection(database.placeDao(), database.todoDao(), fixedClock),
            proStatus = proStatus,
            gate = MaintenanceGate(fixedClock),
            scope = this,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.limitRejected.collect { limits += it } }
        return engine
    }
}
