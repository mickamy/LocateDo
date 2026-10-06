package com.locatedo.locatedo.core.billing

import java.time.Instant

enum class PlanKind {
    ANNUAL,
    MONTHLY,
}

data class ProSubscription(
    val term: PlanKind?,
    val expiresAt: Instant?,
    val willRenew: Boolean,
    val isTrial: Boolean,
    val hasBillingIssue: Boolean,
) {
    companion object {
        // Store identifiers end in the term: "…pro.annual" on the App Store, the "annual" base plan on Google Play.
        fun term(identifier: String): PlanKind? = when {
            identifier.endsWith("annual") -> PlanKind.ANNUAL
            identifier.endsWith("monthly") -> PlanKind.MONTHLY
            else -> null
        }
    }
}

// trialDays is null when the store offers no free trial to this buyer.
data class PaywallPlan(val kind: PlanKind, val price: String, val trialDays: Int?)

sealed interface PurchaseOutcome {
    data object Canceled : PurchaseOutcome
    data class Completed(val subscription: ProSubscription?) : PurchaseOutcome
}
