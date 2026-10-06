package com.locatedo.locatedo.feature.paywall

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsParameters
import com.locatedo.locatedo.core.analytics.InstallDate
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.PaywallPlan
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.MemberRole
import com.revenuecat.purchases.PurchasesException
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PaywallFailure {
    PURCHASE_FAILED,
    NOTHING_TO_RESTORE,
}

data class PaywallUiState(
    val isLoading: Boolean = true,
    val plans: List<PaywallPlan> = emptyList(),
    val selected: PlanKind = PlanKind.ANNUAL,
    val isWorking: Boolean = false,
    val isRestoring: Boolean = false,
    val isMember: Boolean = false,
    val failure: PaywallFailure? = null,
) {
    val selectedPlan: PaywallPlan?
        get() = plans.firstOrNull { it.kind == selected }

    val startsTrial: Boolean
        get() = selectedPlan?.trialDays != null

    val isBusy: Boolean
        get() = isWorking || isRestoring
}

sealed interface PaywallEvent {
    data object Purchased : PaywallEvent
    data object Restored : PaywallEvent
}

// Members cannot buy Pro for a household they do not own, so they only get told who can.
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: Entitlements,
    membershipRepository: MembershipRepository,
    authenticator: Authenticator,
    private val preferences: AppPreferences,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {
    private data class Local(
        val isLoading: Boolean = true,
        val plans: List<PaywallPlan> = emptyList(),
        val selected: PlanKind = PlanKind.ANNUAL,
        val isWorking: Boolean = false,
        val isRestoring: Boolean = false,
        val failure: PaywallFailure? = null,
    )

    private val local = MutableStateFlow(Local())
    private val _events = MutableSharedFlow<PaywallEvent>()
    private var trigger: PaywallTrigger? = null
    private var openedAt: Instant? = null
    private var hasSubscribed = false

    val events: SharedFlow<PaywallEvent> = _events

    val uiState: StateFlow<PaywallUiState> = combine(
        local,
        membershipRepository.observeAll(),
        authenticator.session,
    ) { local, memberships, session ->
        PaywallUiState(
            isLoading = local.isLoading,
            plans = local.plans,
            selected = local.selected,
            isWorking = local.isWorking,
            isRestoring = local.isRestoring,
            isMember = memberships.firstOrNull { it.userId == session?.userId }?.role == MemberRole.MEMBER,
            failure = local.failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PaywallUiState())

    init {
        viewModelScope.launch {
            val plans = try {
                entitlements.plans()
            } catch (e: Exception) {
                Log.w(TAG, "Loading plans failed", e)
                emptyList()
            }
            local.update { it.copy(isLoading = false, plans = plans) }
        }
    }

    // Once per paywall: the screen survives rotation in this ViewModel, so a recreated screen does not count again.
    fun start(trigger: PaywallTrigger) {
        if (this.trigger != null) {
            return
        }
        this.trigger = trigger
        openedAt = clock.instant()
        analytics.log(AnalyticsEvent.PAYWALL_SHOWN, mapOf(AnalyticsParameter.TRIGGER to trigger.key))
    }

    fun select(kind: PlanKind) = local.update { it.copy(selected = kind) }

    fun purchase(activity: Activity) {
        viewModelScope.launch {
            local.update { it.copy(isWorking = true, failure = null) }
            val plan = local.value.selected
            analytics.log(AnalyticsEvent.PURCHASE_STARTED, planParameters(plan))
            try {
                if (entitlements.purchase(activity, plan)) {
                    hasSubscribed = true
                    analytics.log(
                        AnalyticsEvent.PAYWALL_PURCHASED,
                        planParameters(plan) + (AnalyticsParameter.DAYS_SINCE_INSTALL to daysSinceInstall()),
                    )
                    _events.emit(PaywallEvent.Purchased)
                } else {
                    analytics.log(AnalyticsEvent.PURCHASE_CANCELED, planParameters(plan))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Purchase failed", e)
                analytics.log(AnalyticsEvent.PURCHASE_FAILED, planParameters(plan) + (AnalyticsParameter.REASON to reason(e)))
                local.update { it.copy(failure = PaywallFailure.PURCHASE_FAILED) }
            } finally {
                local.update { it.copy(isWorking = false) }
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            local.update { it.copy(isRestoring = true, failure = null) }
            try {
                entitlements.restore()
                if (entitlements.hasEntitlement) {
                    hasSubscribed = true
                    analytics.log(AnalyticsEvent.RESTORE_COMPLETED, mapOf(AnalyticsParameter.RESULT to RESTORED))
                    _events.emit(PaywallEvent.Restored)
                } else {
                    analytics.log(AnalyticsEvent.RESTORE_COMPLETED, mapOf(AnalyticsParameter.RESULT to NOTHING))
                    local.update { it.copy(failure = PaywallFailure.NOTHING_TO_RESTORE) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Restore failed", e)
                analytics.log(
                    AnalyticsEvent.RESTORE_COMPLETED,
                    mapOf(AnalyticsParameter.RESULT to FAILED, AnalyticsParameter.REASON to reason(e)),
                )
                local.update { it.copy(failure = PaywallFailure.PURCHASE_FAILED) }
            } finally {
                local.update { it.copy(isRestoring = false) }
            }
        }
    }

    // The ViewModel goes away with the screen, not on rotation, so this is the one place a dismissal is certain.
    override fun onCleared() {
        val trigger = trigger ?: return
        val openedAt = openedAt ?: return
        if (hasSubscribed) {
            return
        }
        analytics.log(
            AnalyticsEvent.PAYWALL_DISMISSED,
            mapOf(
                AnalyticsParameter.TRIGGER to trigger.key,
                AnalyticsParameter.DURATION_S to Duration.between(openedAt, clock.instant()).seconds.coerceAtLeast(0),
            ),
        )
    }

    private fun planParameters(plan: PlanKind): AnalyticsParameters = mapOf(
        AnalyticsParameter.TRIGGER to (trigger?.key ?: ""),
        AnalyticsParameter.PLAN to plan.name.lowercase(),
    )

    private suspend fun daysSinceInstall(): Int =
        InstallDate.daysSinceInstall(preferences.analytics.first().firstLaunchedAt, clock.instant())

    companion object {
        private const val TAG = "Billing"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val RESTORED = "restored"
        private const val NOTHING = "nothing"
        private const val FAILED = "failed"

        // RevenueCat's codes are numbered the same on both platforms, so this matches what iOS reports.
        fun reason(error: Exception): String {
            if (error is PurchasesException) {
                return "RevenueCat.ErrorCode:${error.code.code}"
            }
            return error.javaClass.simpleName
        }
    }
}
