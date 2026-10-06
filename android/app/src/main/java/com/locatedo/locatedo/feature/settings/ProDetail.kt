package com.locatedo.locatedo.feature.settings

import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.billing.ProSubscription
import com.locatedo.locatedo.core.model.Plan
import java.time.Instant

// The rows under the Pro status in Settings, in the order they are shown.
sealed interface ProDetail {
    data class Term(val kind: PlanKind, val isTrial: Boolean) : ProDetail
    data class Renews(val at: Instant) : ProDetail
    data class Ends(val at: Instant) : ProDetail
    data object AutoRenewOff : ProDetail
    data object BillingIssue : ProDetail
    data object Household : ProDetail

    companion object {
        fun details(subscription: ProSubscription?, plan: Plan?): List<ProDetail> {
            if (subscription == null) {
                return if (plan == Plan.PRO) listOf(Household) else emptyList()
            }
            val details = mutableListOf<ProDetail>()
            subscription.term?.let { details += Term(it, subscription.isTrial) }
            subscription.expiresAt?.let { details += if (subscription.willRenew) Renews(it) else Ends(it) }
            if (!subscription.willRenew) {
                details += AutoRenewOff
            }
            if (subscription.hasBillingIssue) {
                details += BillingIssue
            }
            return details
        }
    }
}
