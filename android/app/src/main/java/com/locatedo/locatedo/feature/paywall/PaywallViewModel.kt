package com.locatedo.locatedo.feature.paywall

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.PaywallPlan
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.model.MemberRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

    fun select(kind: PlanKind) = local.update { it.copy(selected = kind) }

    fun purchase(activity: Activity) {
        viewModelScope.launch {
            local.update { it.copy(isWorking = true, failure = null) }
            try {
                if (entitlements.purchase(activity, local.value.selected)) {
                    _events.emit(PaywallEvent.Purchased)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Purchase failed", e)
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
                    _events.emit(PaywallEvent.Restored)
                } else {
                    local.update { it.copy(failure = PaywallFailure.NOTHING_TO_RESTORE) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Restore failed", e)
                local.update { it.copy(failure = PaywallFailure.PURCHASE_FAILED) }
            } finally {
                local.update { it.copy(isRestoring = false) }
            }
        }
    }

    private companion object {
        const val TAG = "Billing"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
