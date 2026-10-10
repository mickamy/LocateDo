package com.locatedo.locatedo.feature.onboarding

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.SettableClock
import com.locatedo.locatedo.testing.fakeAnalyticsConsent
import com.locatedo.locatedo.testing.testPreferences
import java.time.Instant
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
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val clock = SettableClock(now)
    private val analytics = FakeAnalytics()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startingMovesFromTheIntroToThePrivacyPage() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED)
        val viewModel = viewModel(preferences, permissions)
        assertEquals(OnboardingStep.INTRO, viewModel.step.value)

        viewModel.start()

        assertEquals(OnboardingStep.PRIVACY, viewModel.step.value)
        assertFalse(permissions.locationRequested)
    }

    @Test
    fun asksForNotificationsAfterLocationWhenTheyAreStillUndecided() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED)
        val viewModel = viewModel(preferences, permissions)

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
        val viewModel = viewModel(preferences, permissions)

        viewModel.locationRequested()

        assertEquals(OnboardingStep.INTRO, viewModel.step.value)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
    }

    @Test
    fun skippingNotificationsCompletesOnboardingWithoutAsking() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository()
        val viewModel = viewModel(preferences, permissions)
        viewModel.locationRequested()

        viewModel.skipNotifications()

        assertFalse(permissions.notificationsRequested)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
    }

    @Test
    fun finishingLogsWhatWasGrantedAndHowLongItTook() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED)
        val viewModel = viewModel(preferences, permissions)
        clock.now = now.plusSeconds(15)

        viewModel.locationRequested()

        assertEquals(
            mapOf("location_auth" to "when_in_use", "notification_auth" to "denied", "duration_s" to 15L),
            analytics.values(AnalyticsEvent.ONBOARDING_COMPLETED),
        )
    }

    @Test
    fun asksAboutUsageDataLastInTheEeaAndTheUk() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setAnalyticsConsentRequired(true)
        val permissions = FakePermissionsRepository(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED)
        val viewModel = viewModel(preferences, permissions)
        viewModel.locationRequested()

        viewModel.notificationsRequested()

        assertEquals(OnboardingStep.ANALYTICS, viewModel.step.value)
        assertFalse(preferences.data.first().hasCompletedOnboarding)

        viewModel.answerAnalytics(false)

        assertEquals(false, preferences.analyticsConsent.first().answer)
        assertTrue(preferences.data.first().hasCompletedOnboarding)
        assertEquals(1, analytics.count(AnalyticsEvent.ONBOARDING_COMPLETED))
    }

    @Test
    fun doesNotAskAboutUsageDataElsewhere() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setAnalyticsConsentRequired(false)
        val viewModel = viewModel(preferences, FakePermissionsRepository())
        viewModel.locationRequested()

        viewModel.skipNotifications()

        assertTrue(preferences.data.first().hasCompletedOnboarding)
        assertEquals(null, preferences.analyticsConsent.first().answer)
    }

    private fun viewModel(preferences: AppPreferences, permissions: FakePermissionsRepository) =
        OnboardingViewModel(preferences, permissions, analytics, fakeAnalyticsConsent(preferences, analytics), clock)
}
