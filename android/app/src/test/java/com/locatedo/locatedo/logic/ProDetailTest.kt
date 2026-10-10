package com.locatedo.locatedo.logic

import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.billing.ProSubscription
import com.locatedo.locatedo.core.model.Plan
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProDetailTest {
    private val date = Instant.ofEpochSecond(1_800_000_000)

    @Test
    fun aRenewingSubscriptionShowsItsTermAndNextRenewal() {
        val details = ProDetail.details(subscription(willRenew = true), Plan.PRO)

        assertEquals(listOf(ProDetail.Term(PlanKind.ANNUAL, isTrial = false), ProDetail.Renews(date)), details)
    }

    @Test
    fun aTrialSaysSo() {
        val details = ProDetail.details(subscription(willRenew = true, isTrial = true), Plan.FREE)

        assertEquals(listOf(ProDetail.Term(PlanKind.ANNUAL, isTrial = true), ProDetail.Renews(date)), details)
    }

    @Test
    fun turningOffAutoRenewShowsWhenItEnds() {
        val details = ProDetail.details(subscription(willRenew = false), Plan.PRO)

        assertEquals(listOf(ProDetail.Term(PlanKind.ANNUAL, isTrial = false), ProDetail.Ends(date), ProDetail.AutoRenewOff), details)
    }

    @Test
    fun aBillingIssueIsFlagged() {
        val details = ProDetail.details(subscription(willRenew = true, hasBillingIssue = true), Plan.PRO)

        assertEquals(ProDetail.BillingIssue, details.last())
    }

    @Test
    fun anUnknownProductLeavesTheTermOut() {
        val unknown = ProSubscription(term = null, expiresAt = date, willRenew = true, isTrial = false, hasBillingIssue = false)

        assertEquals(listOf(ProDetail.Renews(date)), ProDetail.details(unknown, Plan.PRO))
    }

    @Test
    fun withoutASubscriptionOnlyTheHouseholdPlanCounts() {
        assertEquals(listOf(ProDetail.Household), ProDetail.details(null, Plan.PRO))
        assertTrue(ProDetail.details(null, Plan.FREE).isEmpty())
        assertTrue(ProDetail.details(null, null).isEmpty())
    }

    private fun subscription(willRenew: Boolean, isTrial: Boolean = false, hasBillingIssue: Boolean = false) = ProSubscription(
        term = PlanKind.ANNUAL,
        expiresAt = date,
        willRenew = willRenew,
        isTrial = isTrial,
        hasBillingIssue = hasBillingIssue,
    )
}
