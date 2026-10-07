package com.locatedo.locatedo.ui

import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.common.TodosRequests
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.push.PromotionsConsent
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakePermissionsRepository
import com.locatedo.locatedo.testing.FakeSyncEngine
import com.locatedo.locatedo.testing.SettableClock
import com.locatedo.locatedo.testing.appStatusStore
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testPromotionsConsent
import java.time.Instant
import java.util.UUID
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
import org.junit.Assert.assertNull
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
    private val clock = SettableClock(Instant.parse("2026-10-06T03:00:00Z"))
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
    fun startsLoadingThenReflectsOnboarding() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setCompletedOnboarding(true)
        val viewModel = viewModel(preferences)
        assertTrue(viewModel.uiState.value.isLoading)

        subscribe(viewModel)

        val state = viewModel.uiState.first { !it.isLoading }
        assertTrue(state.hasCompletedOnboarding)
        assertFalse(state.isExplainingAlwaysLocation)
    }

    @Test
    fun theFirstPlaceOffersAlwaysLocationOnceWhileItIsWhenInUse() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = viewModel(preferences, permissions = FakePermissionsRepository(location = LocationAuth.WHEN_IN_USE))
        subscribe(viewModel)

        viewModel.placeAdded()

        assertTrue(viewModel.uiState.first { it.isExplainingAlwaysLocation }.isExplainingAlwaysLocation)
        assertTrue(preferences.data.first().hasPromptedAlwaysLocation)

        viewModel.dismissAlwaysLocation()
        viewModel.placeAdded()

        assertFalse(viewModel.uiState.first { !it.isExplainingAlwaysLocation }.isExplainingAlwaysLocation)
    }

    @Test
    fun theAlwaysLocationAnswerIsLoggedOnceWithItsDuration() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val viewModel = viewModel(preferences, permissions = FakePermissionsRepository(location = LocationAuth.WHEN_IN_USE))
        subscribe(viewModel)
        viewModel.placeAdded()
        viewModel.uiState.first { it.isExplainingAlwaysLocation }
        clock.now = clock.now.plusSeconds(3)

        viewModel.alwaysLocationAnswered(AlwaysPromptAnswer.ALLOW)
        viewModel.alwaysLocationAnswered(AlwaysPromptAnswer.DISMISSED)
        viewModel.dismissAlwaysLocation()

        assertEquals(1, analytics.count(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED))
        assertEquals(
            mapOf("result" to "allow", "duration_s" to 3L),
            analytics.values(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED),
        )
    }

    @Test
    fun nothingIsOfferedWhenLocationIsAlreadyAlwaysOrNotGranted() = runTest(dispatcher) {
        for (location in listOf(LocationAuth.ALWAYS, LocationAuth.DENIED, LocationAuth.NOT_DETERMINED)) {
            val preferences = testPreferences(folder.newFolder(location.name), backgroundScope)
            val viewModel = viewModel(preferences, permissions = FakePermissionsRepository(location = location))
            subscribe(viewModel)

            viewModel.placeAdded()

            assertEquals(location.name, false, preferences.data.first().hasPromptedAlwaysLocation)
            assertFalse(location.name, viewModel.uiState.first { !it.isLoading }.isExplainingAlwaysLocation)
        }
    }

    @Test
    fun aPendingNoticeShowsOnceAndIsClearedWhenDismissed() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setCompletedOnboarding(true)
        preferences.setPendingRemovedNotice(true)
        val viewModel = viewModel(preferences)
        subscribe(viewModel)

        assertEquals(AppNotice.REMOVED, viewModel.uiState.first { !it.isLoading }.notice)

        viewModel.dismissNotice()

        assertNull(viewModel.uiState.first { it.notice == null }.notice)
        assertFalse(preferences.data.first().hasPendingRemovedNotice)
    }

    @Test
    fun anOpenedInviteWaitsUntilTheTabsTakeIt() = runTest(dispatcher) {
        val preferences = testPreferences(folder.root, backgroundScope)
        val invites = InviteRequests()
        invites.request("invite-token-0123456789")
        val viewModel = viewModel(preferences, invites = invites)

        assertEquals("invite-token-0123456789", viewModel.pendingInvite.value)

        viewModel.inviteConsumed("invite-token-0123456789")

        assertNull(viewModel.pendingInvite.value)
    }

    @Test
    fun thePromotionsSheetIsOfferedOnceWhenDue() = runTest(dispatcher) {
        val preferences = readyForPromotions()
        val promotions = testPromotionsConsent(preferences, backgroundScope, analytics = analytics)
        val viewModel = viewModel(preferences, permissions = notificationsAllowed(), promotions = promotions)
        subscribe(viewModel)

        promotions.checkPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = clock.instant())

        assertTrue(viewModel.uiState.first { it.isAskingPromotions }.isAskingPromotions)
        assertFalse(promotions.promptDue.value)
        assertTrue(preferences.promotions.first().hasShownPrompt)
        assertEquals("authorized", analytics.values(AnalyticsEvent.PROMOTIONS_PROMPT_SHOWN)["notification_auth"])
    }

    @Test
    fun acceptingThePromotionsSheetTurnsConsentOn() = runTest(dispatcher) {
        val preferences = readyForPromotions()
        val promotions = testPromotionsConsent(preferences, backgroundScope, analytics = analytics)
        val viewModel = viewModel(preferences, permissions = notificationsAllowed(), promotions = promotions)
        subscribe(viewModel)
        promotions.checkPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = clock.instant())
        clock.now = clock.now.plusSeconds(5)

        viewModel.answerPromotions(PromotionsConsent.Answer.ACCEPTED)

        assertFalse(viewModel.uiState.first { !it.isAskingPromotions }.isAskingPromotions)
        assertTrue(preferences.promotions.first { it.consent }.consent)
        assertEquals(
            mapOf("result" to "accepted", "duration_s" to 5L),
            analytics.values(AnalyticsEvent.PROMOTIONS_PROMPT_ANSWERED),
        )
    }

    @Test
    fun dismissingThePromotionsSheetIsAnsweredOnceAndLeavesConsentOff() = runTest(dispatcher) {
        val preferences = readyForPromotions()
        val promotions = testPromotionsConsent(preferences, backgroundScope, analytics = analytics)
        val viewModel = viewModel(preferences, permissions = notificationsAllowed(), promotions = promotions)
        subscribe(viewModel)
        promotions.checkPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = clock.instant())

        viewModel.answerPromotions(PromotionsConsent.Answer.DISMISSED)
        viewModel.answerPromotions(PromotionsConsent.Answer.DISMISSED)

        assertEquals(1, analytics.count(AnalyticsEvent.PROMOTIONS_PROMPT_ANSWERED))
        assertEquals("dismissed", analytics.values(AnalyticsEvent.PROMOTIONS_PROMPT_ANSWERED)["result"])
        assertFalse(preferences.promotions.first().consent)
    }

    @Test
    fun thePromotionsSheetGivesWayToAPlaceOpenedFromANotification() = runTest(dispatcher) {
        val preferences = readyForPromotions()
        val selections = PlaceSelectionRequests()
        selections.request(UUID.randomUUID())
        val promotions = testPromotionsConsent(preferences, backgroundScope, analytics = analytics)
        val viewModel = viewModel(preferences, permissions = notificationsAllowed(), selections = selections, promotions = promotions)
        subscribe(viewModel)

        promotions.checkPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = clock.instant())

        assertFalse(promotions.promptDue.value)
        assertFalse(viewModel.uiState.first { !it.isLoading }.isAskingPromotions)
        assertFalse(preferences.promotions.first().hasShownPrompt)
    }

    private suspend fun TestScope.readyForPromotions(): AppPreferences {
        val preferences = testPreferences(folder.root, backgroundScope)
        preferences.setCompletedOnboarding(true)
        preferences.setReceivedArrivalNotification()
        return preferences
    }

    private fun notificationsAllowed() = FakePermissionsRepository(notifications = NotificationAuth.AUTHORIZED)

    private fun TestScope.viewModel(
        preferences: AppPreferences,
        permissions: FakePermissionsRepository = FakePermissionsRepository(),
        selections: PlaceSelectionRequests = PlaceSelectionRequests(),
        invites: InviteRequests = InviteRequests(),
        promotions: PromotionsConsent = testPromotionsConsent(preferences, backgroundScope),
    ): AppViewModel = AppViewModel(
        preferences,
        permissions,
        selections,
        invites,
        PaywallRequests(),
        TodosRequests(),
        FakeSyncEngine(),
        fakeAuthenticator(),
        appStatusStore(preferences, scope = backgroundScope),
        promotions,
        analytics,
        clock,
    )

    private fun TestScope.subscribe(viewModel: AppViewModel) {
        backgroundScope.launch { viewModel.uiState.collect {} }
    }
}
