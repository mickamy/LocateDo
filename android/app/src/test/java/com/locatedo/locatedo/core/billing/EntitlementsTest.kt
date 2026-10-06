package com.locatedo.locatedo.core.billing

import android.app.Activity
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.testing.FakeEntitlementSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log and the Activity the purchase flow takes.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EntitlementsTest {
    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    @Test
    fun proComesFromEitherTheEntitlementOrTheHouseholdPlan() {
        assertTrue(Entitlements.isPro(hasEntitlement = true, plan = Plan.FREE))
        assertTrue(Entitlements.isPro(hasEntitlement = false, plan = Plan.PRO))
        assertFalse(Entitlements.isPro(hasEntitlement = false, plan = Plan.FREE))
        assertFalse(Entitlements.isPro(hasEntitlement = false, plan = null))
    }

    @Test
    fun logsInWithTheLowercasedUserIdAndTakesItsEntitlement() = runTest {
        val source = FakeEntitlementSource(FakeEntitlementSource.ANNUAL)
        val entitlements = Entitlements(source, this)

        entitlements.logIn(UUID.fromString("0199BD00-0000-7000-8000-000000000001"))

        assertEquals(listOf("0199bd00-0000-7000-8000-000000000001"), source.loggedIn)
        assertTrue(entitlements.hasEntitlement)
    }

    @Test
    fun loggingOutDropsTheEntitlement() = runTest {
        val source = FakeEntitlementSource(FakeEntitlementSource.ANNUAL)
        val entitlements = Entitlements(source, this)
        entitlements.logIn(UUID.randomUUID())
        source.subscription = null

        entitlements.logOut()

        assertFalse(entitlements.hasEntitlement)
        assertEquals(1, source.logOuts)
    }

    @Test
    fun aFailedLogInIsLoggedAndChangesNothing() = runTest {
        val source = FakeEntitlementSource(FakeEntitlementSource.ANNUAL)
        source.failure = IllegalStateException("offline")
        val entitlements = Entitlements(source, this)

        entitlements.logIn(UUID.randomUUID())

        assertFalse(entitlements.hasEntitlement)
    }

    @Test
    fun aPurchaseGrantsTheEntitlement() = runTest {
        val entitlements = Entitlements(FakeEntitlementSource(), this)

        val completed = entitlements.purchase(activity, PlanKind.ANNUAL)

        assertTrue(completed)
        assertTrue(entitlements.hasEntitlement)
    }

    @Test
    fun aCanceledPurchaseChangesNothing() = runTest {
        val source = FakeEntitlementSource()
        source.cancelsPurchases = true
        val entitlements = Entitlements(source, this)

        val completed = entitlements.purchase(activity, PlanKind.MONTHLY)

        assertFalse(completed)
        assertFalse(entitlements.hasEntitlement)
    }

    @Test
    fun restoringTakesWhateverTheStoreHas() = runTest {
        val entitlements = Entitlements(FakeEntitlementSource(FakeEntitlementSource.ANNUAL), this)

        entitlements.restore()

        assertTrue(entitlements.hasEntitlement)
    }

    @Test
    fun keepsWhatTheStoreSaysAboutTheSubscription() = runTest {
        val subscription = ProSubscription(
            term = PlanKind.MONTHLY,
            expiresAt = Instant.ofEpochSecond(1_800_000_000),
            willRenew = false,
            isTrial = false,
            hasBillingIssue = false,
        )
        val entitlements = Entitlements(FakeEntitlementSource(subscription), this)

        entitlements.logIn(UUID.randomUUID())

        assertEquals(subscription, entitlements.subscription.value)
    }

    @Test
    fun storeUpdatesArriveOnceStarted() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeEntitlementSource()
        val entitlements = Entitlements(source, backgroundScope)

        entitlements.start()
        entitlements.start()
        source.updates.emit(FakeEntitlementSource.ANNUAL)

        assertTrue(entitlements.hasEntitlement)
        assertEquals(1, source.updates.subscriptionCount.value)
    }

    @Test
    fun withoutAStoreNothingHappens() = runTest {
        val entitlements = Entitlements(UnavailableEntitlementSource, this)

        entitlements.logIn(UUID.randomUUID())
        entitlements.start()

        assertFalse(entitlements.hasEntitlement)
        assertTrue(entitlements.plans().isEmpty())
        assertFalse(entitlements.purchase(activity, PlanKind.ANNUAL))
    }

    @Test
    fun readsTheTermFromTheStoreIdentifier() {
        assertEquals(PlanKind.ANNUAL, ProSubscription.term("com.locatedo.LocateDo.pro.annual"))
        assertEquals(PlanKind.MONTHLY, ProSubscription.term("monthly"))
        assertEquals(null, ProSubscription.term("test_product"))
    }
}
