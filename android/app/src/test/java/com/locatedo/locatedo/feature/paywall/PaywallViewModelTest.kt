package com.locatedo.locatedo.feature.paywall

import android.app.Activity
import androidx.lifecycle.ViewModelStore
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeEntitlementSource
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.SettableClock
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log and the Activity the purchase flow takes.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaywallViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val clock = SettableClock(now)
    private val source = FakeEntitlementSource()
    private val memberships = FakeMembershipRepository()
    private val analytics = FakeAnalytics()
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsThePlansWithTheYearlyOneSelected() = runTest(dispatcher) {
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(PlanKind.ANNUAL, PlanKind.MONTHLY), state.plans.map { it.kind })
        assertEquals(PlanKind.ANNUAL, state.selected)
        assertTrue(state.startsTrial)

        viewModel.select(PlanKind.MONTHLY)

        assertFalse(viewModel.uiState.value.startsTrial)
    }

    @Test
    fun withoutPlansTheStoreIsUnavailable() = runTest(dispatcher) {
        source.plans = emptyList()
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.plans.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun aPurchaseClosesThePaywall() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)

        viewModel.purchase(activity)

        assertEquals(listOf(PaywallEvent.Purchased), events)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun aCanceledPurchaseStaysQuiet() = runTest(dispatcher) {
        source.cancelsPurchases = true
        val viewModel = viewModel()
        val events = events(viewModel)

        viewModel.purchase(activity)

        assertTrue(events.isEmpty())
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun aFailedPurchaseIsExplained() = runTest(dispatcher) {
        val viewModel = viewModel()
        source.failure = IllegalStateException("store down")

        viewModel.purchase(activity)

        assertEquals(PaywallFailure.PURCHASE_FAILED, viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun restoringReportsWhatItFound() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)

        viewModel.restore()
        assertEquals(PaywallFailure.NOTHING_TO_RESTORE, viewModel.uiState.value.failure)

        source.subscription = FakeEntitlementSource.ANNUAL
        viewModel.restore()

        assertEquals(listOf(PaywallEvent.Restored), events)
        assertNull(viewModel.uiState.value.failure)
    }

    @Test
    fun aMemberOnlyGetsTold() = runTest(dispatcher) {
        memberships.state.value = listOf(
            Membership(UUID.randomUUID(), MemberRole.OWNER, "Taro", now, now),
            Membership(testSession.userId, MemberRole.MEMBER, "Hanako", now, now),
        )
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isMember)
    }

    @Test
    fun showingLogsTheTriggerOnce() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.start(PaywallTrigger.SHARE)
        viewModel.start(PaywallTrigger.SHARE)

        assertEquals(1, analytics.count(AnalyticsEvent.PAYWALL_SHOWN))
        assertEquals("share", analytics.values(AnalyticsEvent.PAYWALL_SHOWN)["trigger"])
    }

    @Test
    fun aPurchaseLogsItsStartAndSuccessWithTheInstallAge() = runTest(dispatcher) {
        val preferences = preferences()
        preferences.recordFirstLaunch(now.minus(Duration.ofDays(2)))
        val viewModel = viewModel(preferences)
        viewModel.start(PaywallTrigger.PLACE_LIMIT)

        viewModel.purchase(activity)

        assertEquals(mapOf("trigger" to "place_limit", "plan" to "annual"), analytics.values(AnalyticsEvent.PURCHASE_STARTED))
        val purchased = analytics.values(AnalyticsEvent.PAYWALL_PURCHASED)
        assertEquals("annual", purchased["plan"])
        assertEquals(2L, purchased["days_since_install"])
    }

    @Test
    fun aCanceledPurchaseLogsTheCancellation() = runTest(dispatcher) {
        source.cancelsPurchases = true
        val viewModel = viewModel()
        viewModel.start(PaywallTrigger.TODO_LIMIT)
        viewModel.select(PlanKind.MONTHLY)

        viewModel.purchase(activity)

        assertEquals(mapOf("trigger" to "todo_limit", "plan" to "monthly"), analytics.values(AnalyticsEvent.PURCHASE_CANCELED))
        assertTrue(analytics.names.none { it == AnalyticsEvent.PAYWALL_PURCHASED })
    }

    @Test
    fun aFailedPurchaseLogsTheReason() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(PaywallTrigger.SETTINGS)
        source.failure = PurchasesException(PurchasesError(PurchasesErrorCode.StoreProblemError))

        viewModel.purchase(activity)

        val expected = "RevenueCat.ErrorCode:${PurchasesErrorCode.StoreProblemError.code}"
        assertEquals(expected, analytics.values(AnalyticsEvent.PURCHASE_FAILED)["reason"])
        assertEquals("IllegalStateException", PaywallViewModel.reason(IllegalStateException("store down")))
    }

    @Test
    fun restoringLogsWhatItFound() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.restore()
        assertEquals("nothing", analytics.values(AnalyticsEvent.RESTORE_COMPLETED)["result"])

        source.failure = IllegalStateException("offline")
        viewModel.restore()
        assertEquals(
            mapOf("result" to "failed", "reason" to "IllegalStateException"),
            analytics.values(AnalyticsEvent.RESTORE_COMPLETED),
        )

        source.failure = null
        source.subscription = FakeEntitlementSource.ANNUAL
        viewModel.restore()
        assertEquals("restored", analytics.values(AnalyticsEvent.RESTORE_COMPLETED)["result"])
    }

    @Test
    fun closingWithoutBuyingLogsTheDismissalWithItsDuration() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(PaywallTrigger.SHARE)
        clock.now = now.plusSeconds(42)

        ViewModelStore().apply { put("paywall", viewModel) }.clear()

        assertEquals(mapOf("trigger" to "share", "duration_s" to 42L), analytics.values(AnalyticsEvent.PAYWALL_DISMISSED))
    }

    @Test
    fun closingAfterBuyingIsNotADismissal() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.start(PaywallTrigger.SHARE)
        viewModel.purchase(activity)

        ViewModelStore().apply { put("paywall", viewModel) }.clear()

        assertTrue(analytics.names.none { it == AnalyticsEvent.PAYWALL_DISMISSED })
    }

    private fun TestScope.viewModel(preferences: AppPreferences = preferences()): PaywallViewModel {
        val viewModel = PaywallViewModel(
            Entitlements(source, analytics, backgroundScope),
            memberships,
            fakeAuthenticator(testSession),
            preferences,
            analytics,
            clock,
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.preferences() = testPreferences(folder.root, backgroundScope)

    private fun TestScope.events(viewModel: PaywallViewModel): List<PaywallEvent> {
        val events = mutableListOf<PaywallEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}
