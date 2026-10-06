package com.locatedo.locatedo.feature.settings

import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeEntitlementSource
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakeSyncStateRepository
import com.locatedo.locatedo.testing.InMemorySessionStore
import com.locatedo.locatedo.testing.testPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.util.Log, which a failed restore writes to.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val permissions = FakePermissionsRepository(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED)
    private val source = FakeEntitlementSource()
    private val syncState = FakeSyncStateRepository()
    private val paywalls = PaywallRequests()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun reflectsTheStoredRadiusAndThePermissions() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setDefaultRadiusMeters(250.0)
        val viewModel = viewModel(preferences)

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(LocationAuth.ALWAYS, state.location)
        assertEquals(NotificationAuth.AUTHORIZED, state.notifications)
        assertEquals(250.0, state.defaultRadiusMeters, 0.0)
    }

    @Test
    fun theDefaultRadiusIsPersisted() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = viewModel(preferences)

        viewModel.setDefaultRadius(300.0)

        assertEquals(300.0, preferences.data.first().defaultRadiusMeters, 0.0)
        assertEquals(300.0, viewModel.uiState.first { it.defaultRadiusMeters == 300.0 }.defaultRadiusMeters, 0.0)
    }

    @Test
    fun aPermissionRequestIsRememberedAndTheStatusReread() = runTest(dispatcher) {
        val viewModel = viewModel(testPreferences(folder.root, backgroundScope))

        viewModel.locationRequested()
        viewModel.notificationsRequested()

        assertTrue(permissions.locationRequested)
        assertTrue(permissions.notificationsRequested)
        assertEquals(2, permissions.refreshCount)
        assertFalse(viewModel.uiState.first { !it.isLoading }.isLoading)
    }

    @Test
    fun theProSectionFollowsTheStoreAndTheHousehold() = runTest(dispatcher) {
        val viewModel = viewModel(testPreferences(folder.root, backgroundScope))

        var pro = viewModel.uiState.first { !it.isLoading }.pro
        assertFalse(pro.isPro)
        assertTrue(pro.canUpgrade)
        assertTrue(pro.details.isEmpty())

        syncState.state.value = SyncState(plan = Plan.PRO)

        pro = viewModel.uiState.first { it.pro.isPro }.pro
        assertFalse(pro.hasEntitlement)
        assertFalse(pro.canUpgrade)
        assertEquals(listOf(ProDetail.Household), pro.details)

        viewModel.upgrade()

        assertEquals(PaywallTrigger.SETTINGS, paywalls.pending.value)
    }

    @Test
    fun restoringReportsWhatTheStoreHas() = runTest(dispatcher) {
        val viewModel = viewModel(testPreferences(folder.root, backgroundScope))

        viewModel.restorePurchases()
        assertEquals(RestoreResult.NOTHING, viewModel.uiState.first { it.pro.restoreResult != null }.pro.restoreResult)

        source.subscription = FakeEntitlementSource.ANNUAL
        viewModel.restorePurchases()

        val pro = viewModel.uiState.first { it.pro.restoreResult == RestoreResult.RESTORED }.pro
        assertTrue(pro.hasEntitlement)
        assertTrue(pro.isPro)
        assertFalse(pro.canUpgrade)

        viewModel.dismissRestoreResult()

        assertEquals(null, viewModel.uiState.first { it.pro.restoreResult == null }.pro.restoreResult)
    }

    private fun TestScope.viewModel(preferences: com.locatedo.locatedo.core.datastore.AppPreferences): SettingsViewModel {
        val authenticator = Authenticator(
            InMemorySessionStore(),
            FakeAccountService(),
            AccessTokenStore(),
            Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneOffset.UTC),
            this,
        )
        val viewModel = SettingsViewModel(
            preferences,
            permissions,
            authenticator,
            Entitlements(source, FakeAnalytics(), backgroundScope),
            syncState,
            FakeMembershipRepository(),
            paywalls,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}
