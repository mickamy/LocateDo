package com.locatedo.locatedo.feature.onboarding

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.testPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class OnboardingViewModelTest {
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
    fun asksForNotificationsAfterLocationWhenTheyAreStillUndecided() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED)
        val viewModel = OnboardingViewModel(preferences, permissions)

        viewModel.locationRequested()

        assertTrue(permissions.locationRequested)
        assertEquals(OnboardingStep.NOTIFICATIONS, viewModel.step.value)
        assertFalse(preferences.data.first().hasCompletedOnboarding)

        viewModel.notificationsRequested()

        assertTrue(permissions.notificationsRequested)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
    }

    @Test
    fun finishesRightAfterLocationWhenNotificationsAreAlreadyDecided() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED)
        val viewModel = OnboardingViewModel(preferences, permissions)

        viewModel.locationRequested()

        assertEquals(OnboardingStep.INTRO, viewModel.step.value)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
    }

    @Test
    fun skippingNotificationsCompletesOnboardingWithoutAsking() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository()
        val viewModel = OnboardingViewModel(preferences, permissions)
        viewModel.locationRequested()

        viewModel.skipNotifications()

        assertFalse(permissions.notificationsRequested)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
    }
}
