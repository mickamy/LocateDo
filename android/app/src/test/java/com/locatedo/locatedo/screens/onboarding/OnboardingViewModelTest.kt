package com.locatedo.locatedo.screens.onboarding

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
import org.junit.Assert.assertNull
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
    fun locationIsNeededOnlyUntilItHasBeenAsked() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED)
        val viewModel = viewModel(preferences, permissions)
        assertTrue(viewModel.needsLocation())

        viewModel.locationRequested()
        permissions.state.value = permissions.state.value.copy(location = LocationAuth.WHEN_IN_USE)

        assertTrue(permissions.locationRequested)
        assertFalse(viewModel.needsLocation())
    }

    @Test
    fun theFirstPlaceEndsOnboardingWithItsKindAndName() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val permissions = FakePermissionsRepository(LocationAuth.WHEN_IN_USE, NotificationAuth.NOT_DETERMINED)
        val viewModel = viewModel(preferences, permissions)
        clock.now = now.plusSeconds(42)

        viewModel.firstPlaceSaved(StoreKind.GROCERY, "Corner Market")
        val asks = viewModel.finish()

        assertFalse(asks)
        assertEquals(OnboardingResult(OnboardingChoice.ADD_PLACE, "Corner Market"), viewModel.result.value)
        assertEquals(
            mapOf(
                "choice" to "add_place",
                "kind" to "grocery",
                "location_auth" to "when_in_use",
                "notification_auth" to "not_determined",
                "duration_s" to 42L,
            ),
            analytics.values(AnalyticsEvent.ONBOARDING_COMPLETED),
        )
    }

    @Test
    fun theOtherWaysEndWithoutAPlace() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = viewModel(preferences, FakePermissionsRepository())

        viewModel.choose(OnboardingChoice.SIGN_IN)
        viewModel.finish()

        assertEquals(OnboardingResult(OnboardingChoice.SIGN_IN, null), viewModel.result.value)
        assertEquals("sign_in", analytics.values(AnalyticsEvent.ONBOARDING_COMPLETED)["choice"])
        assertNull(analytics.values(AnalyticsEvent.ONBOARDING_COMPLETED)["kind"])
    }

    @Test
    fun notNowIsTheChoiceWhenNothingElseWasMade() = runTest(dispatcher) {
        val viewModel = viewModel(testPreferences(folder.root, backgroundScope), FakePermissionsRepository())

        viewModel.finish()

        assertEquals(OnboardingResult(OnboardingChoice.LATER, null), viewModel.result.value)
    }

    @Test
    fun asksAboutUsageDataLastInTheEeaAndTheUk() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = viewModel(preferences, FakePermissionsRepository(), inScope = true)

        assertTrue(viewModel.finish())
        assertNull(viewModel.result.value)
        assertEquals(0, analytics.count(AnalyticsEvent.ONBOARDING_COMPLETED))

        viewModel.answerAnalytics(false)

        assertEquals(false, preferences.analyticsConsent.first().answer)
        assertEquals(OnboardingResult(OnboardingChoice.LATER, null), viewModel.result.value)
        assertEquals(1, analytics.count(AnalyticsEvent.ONBOARDING_COMPLETED))
    }

    @Test
    fun todosStartOverOnlyForADifferentStore() = runTest(dispatcher) {
        val viewModel = viewModel(testPreferences(folder.root, backgroundScope), FakePermissionsRepository())

        assertTrue(viewModel.startsNewTodos(FirstStore(StoreKind.GROCERY)))
        assertFalse(viewModel.startsNewTodos(FirstStore(StoreKind.GROCERY)))
        assertTrue(viewModel.startsNewTodos(FirstStore(StoreKind.OTHER, "Bakery")))
        assertTrue(viewModel.startsNewTodos(FirstStore(StoreKind.OTHER, "Bookstore")))
    }

    // The region is set explicitly, so the result does not depend on the machine's locale.
    private suspend fun viewModel(
        preferences: AppPreferences,
        permissions: FakePermissionsRepository,
        inScope: Boolean = false,
    ): OnboardingViewModel {
        preferences.setAnalyticsConsentRequired(inScope)
        return OnboardingViewModel(permissions, analytics, fakeAnalyticsConsent(preferences, analytics), clock)
    }
}
