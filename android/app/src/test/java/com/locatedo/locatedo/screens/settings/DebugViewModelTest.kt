package com.locatedo.locatedo.screens.settings

import com.locatedo.locatedo.core.api.HealthCheck
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.sync.Write
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.testing.FakeSyncEngine
import com.locatedo.locatedo.testing.FakeSyncStateRepository
import com.locatedo.locatedo.testing.fakeAnalyticsConsent
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DebugViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val householdId = UUID.randomUUID()
    private val syncState = FakeSyncStateRepository(SyncState(householdId = householdId, cursor = 42))
    private val syncEngine = FakeSyncEngine()
    private val health = FakeHealthCheck()
    private val authenticator = fakeAuthenticator(testSession)
    private lateinit var database: LocateDoDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun showsTheAccountAndSyncState() = runTest(dispatcher) {
        val queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
        queue.enqueue(Write.put(place("Store")))
        syncEngine.lastPullSummary.value = "3 changes, cursor 40 → 42"

        // Room counts the queue on its own thread, so the state is waited for.
        val state = viewModel(queue).uiState.first { it.queuedWrites == 1 }

        assertEquals(testSession.userId.toString(), state.userId)
        assertEquals(householdId.toString(), state.householdId)
        assertEquals(42L, state.cursor)
        assertEquals(1, state.queuedWrites)
        assertEquals("3 changes, cursor 40 → 42", state.lastPull)
    }

    @Test
    fun syncNowPullsAndTheConnectionCheckShowsTheStatus() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.syncNow()
        viewModel.checkConnection()

        assertEquals(1, syncEngine.syncs)
        assertEquals("HTTP 200", viewModel.uiState.first { it.serverStatus != null }.serverStatus)
    }

    @Test
    fun overridingTheConsentStoreCountryStartsTheAnswerOver() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.consentStoreCountry.collect {} }

        viewModel.setConsentStoreCountry("GB")

        assertEquals("GB", viewModel.consentStoreCountry.first { it != null })
    }

    @Test
    fun aFailedConnectionCheckSaysWhy() = runTest(dispatcher) {
        health.failure = IOException("timeout")
        val viewModel = viewModel()

        viewModel.checkConnection()

        assertTrue(viewModel.uiState.first { it.serverStatus != null }.serverStatus.orEmpty().contains("timeout"))
    }

    private fun TestScope.viewModel(
        queue: WriteQueue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock),
    ): DebugViewModel {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = DebugViewModel(
            authenticator,
            syncState,
            queue,
            syncEngine,
            health,
            preferences,
            fakeAnalyticsConsent(preferences),
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private class FakeHealthCheck : HealthCheck {
        var failure: IOException? = null

        override suspend fun statusCode(): Int {
            failure?.let { throw it }
            return 200
        }
    }
}
