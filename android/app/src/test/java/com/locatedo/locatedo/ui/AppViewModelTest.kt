package com.locatedo.locatedo.ui

import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakeSyncEngine
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
class AppViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsLoadingThenReflectsOnboarding() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setCompletedOnboarding(true)
        val viewModel = AppViewModel(preferences, FakePermissionsRepository(), PlaceSelectionRequests(), FakeSyncEngine())
        assertTrue(viewModel.uiState.value.isLoading)

        subscribe(viewModel)

        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue(state.hasCompletedOnboarding)
        assertFalse(state.isExplainingAlwaysLocation)
    }

    @Test
    fun theFirstPlaceOffersAlwaysLocationOnceWhileItIsWhenInUse() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = AppViewModel(preferences, FakePermissionsRepository(location = LocationAuth.WHEN_IN_USE), PlaceSelectionRequests(), FakeSyncEngine())
        subscribe(viewModel)

        viewModel.placeAdded()

        assertTrue(viewModel.uiState.first { it.isExplainingAlwaysLocation }.isExplainingAlwaysLocation)
        assertTrue(preferences.data.first().hasPromptedAlwaysLocation)

        viewModel.dismissAlwaysLocation()
        viewModel.placeAdded()

        assertFalse(viewModel.uiState.first { !it.isExplainingAlwaysLocation }.isExplainingAlwaysLocation)
    }

    @Test
    fun nothingIsOfferedWhenLocationIsAlreadyAlwaysOrNotGranted() = runTest(dispatcher) {
        for (location in listOf(LocationAuth.ALWAYS, LocationAuth.DENIED, LocationAuth.NOT_DETERMINED)) {
            val preferences = testPreferences(folder.newFolder(location.name), backgroundScope)
            val viewModel = AppViewModel(preferences, FakePermissionsRepository(location = location), PlaceSelectionRequests(), FakeSyncEngine())
            subscribe(viewModel)

            viewModel.placeAdded()

            assertEquals(location.name, false, preferences.data.first().hasPromptedAlwaysLocation)
            assertFalse(location.name, viewModel.uiState.first { !it.isLoading }.isExplainingAlwaysLocation)
        }
    }

    private fun TestScope.subscribe(viewModel: AppViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }
}
