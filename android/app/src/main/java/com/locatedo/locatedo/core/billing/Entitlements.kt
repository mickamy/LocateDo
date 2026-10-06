package com.locatedo.locatedo.core.billing

import android.app.Activity
import android.util.Log
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.sync.ProtoInput
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// What the store says about this user's Pro subscription. The household plan from sync is the other half of "Pro".
@Singleton
class Entitlements @Inject constructor(
    private val source: EntitlementSource,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _subscription = MutableStateFlow<ProSubscription?>(null)
    private var isListening = false

    val subscription: StateFlow<ProSubscription?> = _subscription

    val hasEntitlement: Boolean
        get() = _subscription.value != null

    fun start() {
        if (isListening) {
            return
        }
        isListening = true
        scope.launch {
            source.updates().collect { _subscription.value = it }
        }
    }

    // The store's app user id is the server's user id, so iOS and Android see one subscription.
    suspend fun logIn(userId: UUID) {
        try {
            _subscription.value = source.logIn(ProtoInput.id(userId))
        } catch (e: Exception) {
            Log.w(TAG, "RevenueCat logIn failed", e)
        }
    }

    suspend fun logOut() {
        try {
            _subscription.value = source.logOut()
        } catch (e: Exception) {
            Log.w(TAG, "RevenueCat logOut failed", e)
        }
    }

    suspend fun plans(): List<PaywallPlan> = source.plans()

    // Returns false when the buyer cancels.
    suspend fun purchase(activity: Activity, kind: PlanKind): Boolean =
        when (val outcome = source.purchase(activity, kind)) {
            PurchaseOutcome.Canceled -> false
            is PurchaseOutcome.Completed -> {
                _subscription.value = outcome.subscription
                true
            }
        }

    suspend fun restore() {
        _subscription.value = source.restore()
    }

    companion object {
        private const val TAG = "Billing"

        fun isPro(hasEntitlement: Boolean, plan: com.locatedo.locatedo.core.model.Plan?): Boolean =
            hasEntitlement || plan == com.locatedo.locatedo.core.model.Plan.PRO
    }
}
