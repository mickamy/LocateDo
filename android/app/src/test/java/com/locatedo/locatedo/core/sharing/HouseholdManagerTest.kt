package com.locatedo.locatedo.core.sharing

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.locatedo.household.v1.Plan
import com.locatedo.household.v1.acceptInviteResponse
import com.locatedo.household.v1.household
import com.locatedo.locatedo.core.account.AccountManager
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.data.FakeProStatus
import com.locatedo.locatedo.core.data.LocalData
import com.locatedo.locatedo.core.data.RoomCategoryRepository
import com.locatedo.locatedo.core.data.RoomPlaceRepository
import com.locatedo.locatedo.core.data.RoomSyncStateRepository
import com.locatedo.locatedo.core.data.RoomTodoRepository
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.push.DeviceRegistration
import com.locatedo.locatedo.core.sync.ChangeApplier
import com.locatedo.locatedo.core.sync.DefaultSyncEngine
import com.locatedo.locatedo.core.sync.LimitRejection
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.core.sync.WriteSender
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.FakeDeviceService
import com.locatedo.locatedo.testing.FakeEntitlementSource
import com.locatedo.locatedo.testing.FakeHouseholdService
import com.locatedo.locatedo.testing.FakeInstallationIdSource
import com.locatedo.locatedo.testing.FakeSyncService
import com.locatedo.locatedo.testing.FakeWriteServices
import com.locatedo.locatedo.testing.InMemorySessionStore
import com.locatedo.locatedo.testing.failure
import com.locatedo.locatedo.testing.success
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HouseholdManagerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: LocateDoDatabase
    private val household = FakeHouseholdService()
    private val services = FakeWriteServices()
    private val pulls = FakeSyncService()
    private val householdId = UUID.fromString("0199bd00-0000-7000-8000-0000000000aa")
    private val otherId = UUID.fromString("0199bd00-0000-7000-8000-000000000002")

    @Before
    fun setUp() {
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun removingAMemberSendsTheIdsAndPulls() = runTest {
        val fixture = fixture()

        fixture.manager.remove(otherId)

        val request = household.removed.single()
        assertEquals(householdId.toString(), request.householdId)
        assertEquals(otherId.toString(), request.userId)
        assertTrue(pulls.cursors.isNotEmpty())
    }

    @Test
    fun leavingSendsQueuedWritesThenStartsOverInANewHousehold() = runTest {
        val fixture = fixture()
        fixture.places.add(place("Store"))

        fixture.manager.leave()

        assertEquals(listOf("putPlace"), services.sent)
        assertEquals(listOf(testSession.userId.toString()), household.removed.map { it.userId })
        assertTrue(fixture.places.observeAll().first().isEmpty())
        val created = checkNotNull(household.lastCreate)
        assertEquals(0, created.placesCount)
        assertEquals(BuiltinCategory.entries.size, created.categoriesCount)
        val newHouseholdId = fixture.syncState.get().householdId
        assertEquals(created.id, newHouseholdId.toString())
        assertNotEquals(householdId, newHouseholdId)
    }

    @Test
    fun aFailedLeaveKeepsEverything() = runTest {
        val fixture = fixture()
        fixture.places.add(place("Store"))
        household.failRemove = true

        try {
            fixture.manager.leave()
            fail("expected the leave to fail")
        } catch (e: ConnectException) {
            assertEquals(Code.UNAVAILABLE, e.code)
        }

        assertEquals(1, fixture.places.observeAll().first().size)
        assertEquals(householdId, fixture.syncState.get().householdId)
        assertEquals(0, household.createCalls)
    }

    @Test
    fun aRemovalStartsOverAndSetsTheNotice() = runTest {
        val fixture = fixture()
        fixture.places.add(place("Store"))

        fixture.manager.handleRemoval()

        assertTrue(fixture.places.observeAll().first().isEmpty())
        assertEquals(1, household.createCalls)
        assertTrue(fixture.preferences.data.first().hasPendingRemovedNotice)
    }

    @Test
    fun anInviteIsALinkOnTheOwnedDomain() = runTest {
        val fixture = fixture()

        val invite = fixture.manager.createInvite()

        assertEquals("https://locatedo.com/i/invite-token-0123456789", invite.url)
        assertEquals(Instant.ofEpochSecond(1_800_000_000), invite.expiresAt)
    }

    @Test
    fun acceptingSendsQueuedWritesThenMovesToTheNewHousehold() = runTest {
        val fixture = fixture()
        fixture.places.add(place("Store"))
        val joined = UUID.fromString("0199bd00-0000-7000-8000-0000000000bb")
        household.acceptResponse = success(
            acceptInviteResponse {
                household = household {
                    id = joined.toString()
                    plan = Plan.PLAN_PRO
                }
            },
        )

        fixture.manager.accept("invite-token-0123456789")

        assertEquals(listOf("putPlace"), services.sent)
        assertEquals(listOf("invite-token-0123456789"), household.acceptedTokens)
        assertTrue(fixture.places.observeAll().first().isEmpty())
        val state = fixture.syncState.get()
        assertEquals(joined, state.householdId)
        assertEquals(0L, state.cursor)
        assertEquals(com.locatedo.locatedo.core.model.Plan.PRO, state.plan)
    }

    @Test
    fun aRejectedInviteKeepsEverything() = runTest {
        val fixture = fixture()
        fixture.places.add(place("Store"))
        household.acceptResponse = failure(Code.FAILED_PRECONDITION)

        try {
            fixture.manager.accept("invite-token-0123456789")
            fail("expected the invite to be rejected")
        } catch (e: ConnectException) {
            assertEquals(Code.FAILED_PRECONDITION, e.code)
        }

        assertEquals(1, fixture.places.observeAll().first().size)
        assertEquals(householdId, fixture.syncState.get().householdId)
    }

    private suspend fun TestScope.fixture(): Fixture {
        val authenticator = Authenticator(InMemorySessionStore(testSession), FakeAccountService(), AccessTokenStore(), fixedClock, this)
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        val places = RoomPlaceRepository(database, database.placeDao(), FakeProStatus(), queue, fixedClock)
        val todos = RoomTodoRepository(database, database.todoDao(), FakeProStatus(), queue, fixedClock)
        val categories = RoomCategoryRepository(database, database.categoryDao(), queue, fixedClock)
        val syncState = RoomSyncStateRepository(database.syncStateDao())
        syncState.set(SyncState(householdId = householdId))
        val localData = LocalData(
            database,
            database.placeDao(),
            database.todoDao(),
            database.categoryDao(),
            database.membershipDao(),
            database.pendingWriteDao(),
            database.syncStateDao(),
            categories,
        )
        val preferences = testPreferences(folder.root, backgroundScope)
        val account = AccountManager(
            account = FakeAccountService(),
            household = household,
            authenticator = authenticator,
            placeRepository = places,
            todoRepository = todos,
            categoryRepository = categories,
            syncState = syncState,
            localData = localData,
            queue = queue,
            deviceRegistration = DeviceRegistration(FakeDeviceService(), authenticator, FakeInstallationIdSource()),
            entitlements = Entitlements(FakeEntitlementSource(), this),
            preferences = preferences,
            clock = fixedClock,
        )
        val sync = DefaultSyncEngine(
            sender = WriteSender(services, services, services, authenticator),
            syncService = pulls,
            authenticator = authenticator,
            database = database,
            pendingWrites = database.pendingWriteDao(),
            queue = queue,
            syncState = syncState,
            applier = ChangeApplier(database.categoryDao(), database.placeDao(), database.todoDao(), database.membershipDao()),
            limitRejection = LimitRejection(database.placeDao(), database.todoDao(), fixedClock),
            proStatus = FakeProStatus(),
            scope = this,
        )
        val manager = DefaultHouseholdManager(household, authenticator, syncState, account, sync, preferences)
        return Fixture(manager, places, syncState, preferences)
    }

    class Fixture(
        val manager: DefaultHouseholdManager,
        val places: RoomPlaceRepository,
        val syncState: RoomSyncStateRepository,
        val preferences: com.locatedo.locatedo.core.datastore.AppPreferences,
    )
}
