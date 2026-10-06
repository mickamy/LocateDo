package com.locatedo.locatedo.testing

import android.app.Activity
import com.locatedo.locatedo.core.billing.EntitlementSource
import com.locatedo.locatedo.core.billing.PaywallPlan
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.billing.ProSubscription
import com.locatedo.locatedo.core.billing.PurchaseOutcome
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeEntitlementSource(var subscription: ProSubscription? = null) : EntitlementSource {
    val loggedIn = mutableListOf<String>()
    var logOuts = 0
    var restores = 0
    val analyticsIds = mutableListOf<String>()
    var cancelsPurchases = false
    var failure: Exception? = null
    var plans = listOf(
        PaywallPlan(PlanKind.ANNUAL, "$14.99", trialDays = 7),
        PaywallPlan(PlanKind.MONTHLY, "$2.99", trialDays = null),
    )
    val updates = MutableSharedFlow<ProSubscription?>()

    override suspend fun logIn(appUserId: String): ProSubscription? {
        failure?.let { throw it }
        loggedIn += appUserId
        return subscription
    }

    override suspend fun logOut(): ProSubscription? {
        failure?.let { throw it }
        logOuts += 1
        return subscription
    }

    override suspend fun refresh(): ProSubscription? = subscription

    override fun updates(): Flow<ProSubscription?> = updates

    override suspend fun plans(): List<PaywallPlan> {
        failure?.let { throw it }
        return plans
    }

    override suspend fun purchase(activity: Activity, kind: PlanKind): PurchaseOutcome {
        failure?.let { throw it }
        if (cancelsPurchases) {
            return PurchaseOutcome.Canceled
        }
        subscription = ANNUAL
        return PurchaseOutcome.Completed(ANNUAL)
    }

    override suspend fun restore(): ProSubscription? {
        failure?.let { throw it }
        restores += 1
        return subscription
    }

    override fun setAnalyticsId(instanceId: String) {
        analyticsIds += instanceId
    }

    companion object {
        val ANNUAL = ProSubscription(
            term = PlanKind.ANNUAL,
            expiresAt = Instant.ofEpochSecond(1_800_000_000),
            willRenew = true,
            isTrial = false,
            hasBillingIssue = false,
        )
    }
}
