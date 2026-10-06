package com.locatedo.locatedo.core.account

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.locatedo.account.v1.signInWithGoogleResponse
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.data.FakeProStatus
import com.locatedo.locatedo.core.data.LocalData
import com.locatedo.locatedo.core.data.RoomCategoryRepository
import com.locatedo.locatedo.core.data.RoomPlaceRepository
import com.locatedo.locatedo.core.data.RoomSyncStateRepository
import com.locatedo.locatedo.core.data.RoomTodoRepository
import com.locatedo.locatedo.core.data.category
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.todo
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.push.DeviceRegistration
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.FakeDeviceService
import com.locatedo.locatedo.testing.FakeHouseholdService
import com.locatedo.locatedo.testing.FakeInstallationIdSource
import com.locatedo.locatedo.testing.InMemorySessionStore
import com.locatedo.locatedo.testing.sessionProto
import com.locatedo.locatedo.testing.success
import com.locatedo.locatedo.testing.testPreferences
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AccountManagerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: LocateDoDatabase
    private lateinit var places: RoomPlaceRepository
    private lateinit var todos: RoomTodoRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var syncState: RoomSyncStateRepository
    private lateinit var localData: LocalData
    private lateinit var queue: WriteQueue
    private val account = FakeAccountService()
    private val household = FakeHouseholdService()
    private val devices = FakeDeviceService()
    private val sessionStore = InMemorySessionStore()
    private val authenticator = Authenticator(sessionStore, account, AccessTokenStore(), fixedClock, CoroutineScope(Dispatchers.Unconfined))
    private val householdId = "0199bd00-0000-7000-8000-0000000000aa"

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        places = RoomPlaceRepository(database, database.placeDao(), FakeProStatus(), queue, fixedClock)
        todos = RoomTodoRepository(database, database.todoDao(), FakeProStatus(), queue, fixedClock)
        categories = RoomCategoryRepository(database, database.categoryDao(), queue, fixedClock)
        syncState = RoomSyncStateRepository(database.syncStateDao())
        localData = LocalData(
            database,
            database.placeDao(),
            database.todoDao(),
            database.categoryDao(),
            database.membershipDao(),
            database.pendingWriteDao(),
            database.syncStateDao(),
            categories,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aNewUserUploadsLocalDataAndStoresTheHousehold() = runTest {
        categories.ensureBuiltins()
        val store = place("Store")
        places.add(store)
        todos.add(todo("Milk", store.id))
        account.signInResponse = success(signInResponse(householdId = null))
        val (manager, preferences) = manager()

        manager.signInWithGoogle("google-id-token", "0123456789abcdef")

        val sent = account.signIns.single()
        assertEquals("google-id-token", sent.idToken)
        assertEquals("0123456789abcdef", sent.nonce)
        assertTrue(manager.isSignedIn())
        val created = household.lastCreate
        assertNotNull(created)
        assertEquals(listOf("Store"), created?.placesList?.map { it.name })
        assertEquals(listOf("Milk"), created?.todosList?.map { it.todo.title })
        assertEquals(BuiltinCategory.entries.size, created?.categoriesCount)
        val state = syncState.get()
        assertEquals(created?.id, state.householdId.toString())
        assertEquals(42L, state.cursor)
        assertEquals("the device is registered once the household exists", listOf("installation-1"), devices.registered.map { it.pushToken })
        assertEquals(true, preferences.data.first().hasCompletedOnboarding)
    }

    @Test
    fun anExistingHouseholdIsAdoptedWithoutUploading() = runTest {
        account.signInResponse = success(signInResponse(householdId))
        val (manager, _) = manager()

        manager.signInWithGoogle("google-id-token", "0123456789abcdef")

        assertEquals(0, household.createCalls)
        assertTrue(manager.isSignedIn())
        assertFalse(manager.uiState.first().needsReplaceConfirmation)
        assertEquals(UUID.fromString(householdId), syncState.get().householdId)
        assertEquals(0L, syncState.get().cursor)
        assertTrue("the household's categories arrive with the first pull", categories.observeAll().first().isEmpty())
    }

    @Test
    fun localPlacesAreReplacedOnlyAfterConfirmation() = runTest {
        categories.ensureBuiltins()
        val store = place("Store")
        places.add(store)
        todos.add(todo("Milk", store.id))
        account.signInResponse = success(signInResponse(householdId))
        val (manager, _) = manager()

        manager.signInWithGoogle("google-id-token", "0123456789abcdef")

        assertTrue(manager.uiState.first().needsReplaceConfirmation)
        assertFalse(manager.isSignedIn())
        assertEquals(1, places.observeAll().first().size)

        manager.confirmReplacingLocalData()

        assertFalse(manager.uiState.first().needsReplaceConfirmation)
        assertTrue(manager.isSignedIn())
        assertTrue(places.observeAll().first().isEmpty())
        assertTrue(todos.observeAll().first().isEmpty())
        assertTrue(categories.observeAll().first().isEmpty())
        assertEquals(UUID.fromString(householdId), syncState.get().householdId)
        assertEquals(0, household.createCalls)
    }

    @Test
    fun customCategoriesAlsoNeedConfirmation() = runTest {
        categories.ensureBuiltins()
        categories.add(category("Gym"))
        account.signInResponse = success(signInResponse(householdId))
        val (manager, _) = manager()

        manager.signInWithGoogle("google-id-token", "0123456789abcdef")

        assertTrue(manager.uiState.first().needsReplaceConfirmation)
    }

    @Test
    fun cancelingTheReplacementKeepsLocalDataAndStaysSignedOut() = runTest {
        categories.ensureBuiltins()
        places.add(place("Store"))
        account.signInResponse = success(signInResponse(householdId))
        val (manager, _) = manager()
        manager.signInWithGoogle("google-id-token", "0123456789abcdef")

        manager.cancelReplacingLocalData()
        manager.confirmReplacingLocalData()

        assertFalse(manager.uiState.first().needsReplaceConfirmation)
        assertFalse(manager.isSignedIn())
        assertEquals(1, places.observeAll().first().size)
        assertNull(syncState.get().householdId)
    }

    @Test
    fun anEndedSessionClearsLocalDataButKeepsPreferences() = runTest {
        categories.ensureBuiltins()
        account.signInResponse = success(signInResponse(householdId = null))
        val (manager, preferences) = manager()
        manager.signInWithGoogle("google-id-token", "0123456789abcdef")
        val store = place("Store")
        places.add(store)
        todos.add(todo("Milk", store.id))

        manager.endSession()

        assertTrue(places.observeAll().first().isEmpty())
        assertTrue(todos.observeAll().first().isEmpty())
        assertNull(syncState.get().householdId)
        assertEquals(BuiltinCategory.entries.size, categories.observeAll().first().size)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
        assertTrue(preferences.data.first().hasPendingSessionEndedNotice)
    }

    @Test
    fun signingOutRevokesTheSessionAndClearsLocalData() = runTest {
        categories.ensureBuiltins()
        account.signInResponse = success(signInResponse(householdId = null))
        val (manager, _) = manager()
        manager.signInWithGoogle("google-id-token", "0123456789abcdef")
        places.add(place("Store"))

        manager.signOut()

        val signOut = account.signOuts.single()
        assertEquals("refresh", signOut.refreshToken)
        assertEquals("installation-1", signOut.device.pushToken)
        assertFalse(manager.isSignedIn())
        assertNull(sessionStore.session)
        assertTrue(places.observeAll().first().isEmpty())
        assertNull(syncState.get().householdId)
        assertEquals(BuiltinCategory.entries.size, categories.observeAll().first().size)
    }

    @Test
    fun aFailedServerSignOutStillSignsOutLocally() = runTest {
        account.signInResponse = success(signInResponse(householdId = null))
        account.signOutFailure = Code.UNAVAILABLE
        val (manager, _) = manager()
        manager.signInWithGoogle("google-id-token", "0123456789abcdef")

        manager.signOut()

        assertFalse(manager.isSignedIn())
    }

    @Test
    fun aFailedUploadRetriesWithTheSameHouseholdId() = runTest {
        categories.ensureBuiltins()
        account.signInResponse = success(signInResponse(householdId = null))
        household.failNextCreate = true
        val (manager, _) = manager()

        try {
            manager.signInWithGoogle("google-id-token", "0123456789abcdef")
            fail("expected the upload to fail")
        } catch (e: ConnectException) {
            assertEquals(Code.UNAVAILABLE, e.code)
        }
        val firstId = household.lastCreate?.id
        assertNotNull(firstId)
        assertNull(syncState.get().householdId)
        assertTrue(manager.isSignedIn())

        manager.uploadLocalDataIfNeeded()

        assertEquals(2, household.createCalls)
        assertEquals(firstId, household.lastCreate?.id)
        assertEquals(firstId, syncState.get().householdId.toString())
    }

    @Test
    fun deletingTheAccountResetsLocalDataAndPreferences() = runTest {
        categories.ensureBuiltins()
        account.signInResponse = success(signInResponse(householdId = null))
        val (manager, preferences) = manager()
        manager.signInWithGoogle("google-id-token", "0123456789abcdef")
        val store = place("Store")
        places.add(store)
        todos.add(todo("Milk", store.id))

        manager.deleteAccount()

        assertEquals(1, account.deleteCalls)
        assertFalse(manager.isSignedIn())
        assertTrue(places.observeAll().first().isEmpty())
        assertTrue(todos.observeAll().first().isEmpty())
        assertNull(syncState.get().householdId)
        assertEquals(BuiltinCategory.entries.size, categories.observeAll().first().size)
        assertFalse(preferences.data.first().hasCompletedOnboarding)
    }

    @Test
    fun unsyncedWritesAreTheQueuedOnes() = runTest {
        categories.ensureBuiltins()
        account.signInResponse = success(signInResponse(householdId = null))
        val (manager, _) = manager()
        manager.signInWithGoogle("google-id-token", "0123456789abcdef")
        assertFalse(manager.hasUnsyncedWrites())

        places.add(place("Store"))

        assertTrue(manager.hasUnsyncedWrites())

        manager.signOut()

        assertFalse(manager.hasUnsyncedWrites())
    }

    private suspend fun TestScope.manager(): Pair<AccountManager, AppPreferences> {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setCompletedOnboarding(true)
        val registration = DeviceRegistration(devices, authenticator, FakeInstallationIdSource("installation-1"))
        val manager = AccountManager(
            account = account,
            household = household,
            authenticator = authenticator,
            placeRepository = places,
            todoRepository = todos,
            categoryRepository = categories,
            syncState = syncState,
            localData = localData,
            queue = queue,
            deviceRegistration = registration,
            preferences = preferences,
            clock = fixedClock,
        )
        return manager to preferences
    }

    private fun signInResponse(householdId: String?) = signInWithGoogleResponse {
        session = sessionProto(FakeAccountService.USER_ID, "access", "refresh", fixedNow.plusSeconds(3600))
        householdId?.let { this.householdId = it }
    }
}
