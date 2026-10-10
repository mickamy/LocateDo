package com.locatedo.locatedo.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// The store-facing side of billing; the app only ever sees ProSubscription and PaywallPlan.
interface EntitlementSource {
    suspend fun logIn(appUserId: String): ProSubscription?

    suspend fun logOut(): ProSubscription?

    suspend fun refresh(): ProSubscription?

    fun updates(): Flow<ProSubscription?>

    suspend fun plans(): List<PaywallPlan>

    suspend fun purchase(activity: Activity, kind: PlanKind): PurchaseOutcome

    suspend fun restore(): ProSubscription?

    // null takes the id away, when usage analytics is off.
    fun setAnalyticsId(instanceId: String?)
}

// Builds without a RevenueCat key: nothing to buy, nothing active.
object UnavailableEntitlementSource : EntitlementSource {
    override suspend fun logIn(appUserId: String): ProSubscription? = null

    override suspend fun logOut(): ProSubscription? = null

    override suspend fun refresh(): ProSubscription? = null

    override fun updates(): Flow<ProSubscription?> = emptyFlow()

    override suspend fun plans(): List<PaywallPlan> = emptyList()

    override suspend fun purchase(activity: Activity, kind: PlanKind): PurchaseOutcome = PurchaseOutcome.Canceled

    override suspend fun restore(): ProSubscription? = null

    override fun setAnalyticsId(instanceId: String?) = Unit
}
