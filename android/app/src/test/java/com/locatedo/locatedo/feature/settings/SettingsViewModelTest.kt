package com.locatedo.locatedo.feature.settings

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.testPreferences
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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val permissions = FakePermissionsRepository(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED)

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

    private fun TestScope.viewModel(preferences: com.locatedo.locatedo.core.datastore.AppPreferences): SettingsViewModel {
        val viewModel = SettingsViewModel(preferences, permissions)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}
