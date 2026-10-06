package com.locatedo.locatedo.feature.paywall

import android.app.Activity
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.testing.FakeEntitlementSource
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log and the Activity the purchase flow takes.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaywallViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val source = FakeEntitlementSource()
    private val memberships = FakeMembershipRepository()
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

    private fun TestScope.viewModel(): PaywallViewModel {
        val viewModel = PaywallViewModel(Entitlements(source, backgroundScope), memberships, fakeAuthenticator(testSession))
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.events(viewModel: PaywallViewModel): List<PaywallEvent> {
        val events = mutableListOf<PaywallEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}
