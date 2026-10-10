package com.locatedo.locatedo.core.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// RevenueCat over Google Play Billing. One instance per process: the SDK is configured once, here.
class RevenueCatEntitlementSource(context: Context, apiKey: String) : EntitlementSource {
    private val purchases: Purchases = Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
    private var packages: Map<PlanKind, Package> = emptyMap()

    override suspend fun logIn(appUserId: String): ProSubscription? = subscription(purchases.awaitLogIn(appUserId).customerInfo)

    // The SDK refuses to log out an anonymous user; there is nothing to detach then.
    override suspend fun logOut(): ProSubscription? {
        if (purchases.isAnonymous) {
            return refresh()
        }
        return subscription(purchases.awaitLogOut())
    }

    override suspend fun refresh(): ProSubscription? = subscription(purchases.awaitCustomerInfo())

    override fun setAnalyticsId(instanceId: String?) = purchases.setFirebaseAppInstanceID(instanceId)

    override fun updates(): Flow<ProSubscription?> = callbackFlow {
        purchases.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info -> trySend(subscription(info)) }
        awaitClose { purchases.removeUpdatedCustomerInfoListener() }
    }

    override suspend fun plans(): List<PaywallPlan> {
        val offering = purchases.awaitOfferings().current ?: return emptyList()
        val found = buildMap {
            offering.annual?.let { put(PlanKind.ANNUAL, it) }
            offering.monthly?.let { put(PlanKind.MONTHLY, it) }
        }
        packages = found
        return listOf(PlanKind.ANNUAL, PlanKind.MONTHLY).mapNotNull { kind ->
            val pkg = found[kind] ?: return@mapNotNull null
            PaywallPlan(kind, pkg.product.price.formatted, trialDays(pkg))
        }
    }

    override suspend fun purchase(activity: Activity, kind: PlanKind): PurchaseOutcome {
        val pkg = packages[kind]
            ?: throw PurchasesException(PurchasesError(PurchasesErrorCode.ProductNotAvailableForPurchaseError))
        return try {
            val result = purchases.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            PurchaseOutcome.Completed(subscription(result.customerInfo))
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseOutcome.Canceled else throw e
        }
    }

    override suspend fun restore(): ProSubscription? = subscription(purchases.awaitRestore())

    private fun subscription(info: CustomerInfo): ProSubscription? {
        val entitlement = info.entitlements[ENTITLEMENT_ID]?.takeIf { it.isActive } ?: return null
        return ProSubscription(
            term = ProSubscription.term(entitlement.productPlanIdentifier ?: entitlement.productIdentifier),
            expiresAt = entitlement.expirationDate?.toInstant(),
            willRenew = entitlement.willRenew,
            isTrial = entitlement.periodType == PeriodType.TRIAL,
            hasBillingIssue = entitlement.billingIssueDetectedAt != null,
        )
    }

    // Play leaves the trial out of the options when this buyer is not eligible for it.
    private fun trialDays(pkg: Package): Int? {
        val period = pkg.product.subscriptionOptions?.freeTrial?.freePhase?.billingPeriod ?: return null
        return when (period.unit) {
            Period.Unit.DAY -> period.value
            Period.Unit.WEEK -> period.value * DAYS_PER_WEEK
            Period.Unit.MONTH -> period.value * DAYS_PER_MONTH
            Period.Unit.YEAR -> period.value * DAYS_PER_YEAR
            Period.Unit.UNKNOWN -> null
        }
    }

    private companion object {
        const val ENTITLEMENT_ID = "locatedo_pro"
        const val DAYS_PER_WEEK = 7
        const val DAYS_PER_MONTH = 30
        const val DAYS_PER_YEAR = 365
    }
}
