package com.locatedo.locatedo.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AlwaysPromptTracker
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.PermissionAction
import com.locatedo.locatedo.core.analytics.PermissionKind
import com.locatedo.locatedo.core.analytics.logPermissionAction
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.push.PromotionsConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RestoreResult {
    RESTORED,
    NOTHING,
    FAILED,
}

data class ProUiState(
    val isPro: Boolean = false,
    val hasEntitlement: Boolean = false,
    val isMember: Boolean = false,
    val details: List<ProDetail> = emptyList(),
    val isRestoring: Boolean = false,
    val restoreResult: RestoreResult? = null,
) {
    val canUpgrade: Boolean
        get() = !isPro && !isMember

    val canRestore: Boolean
        get() = !isMember
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val location: LocationAuth = LocationAuth.NOT_DETERMINED,
    val notifications: NotificationAuth = NotificationAuth.NOT_DETERMINED,
    val promotionsConsent: Boolean = false,
    val defaultRadiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
    val pro: ProUiState = ProUiState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    authenticator: Authenticator,
    private val entitlements: Entitlements,
    syncStateRepository: SyncStateRepository,
    membershipRepository: MembershipRepository,
    private val paywallRequests: PaywallRequests,
    private val promotionsConsent: PromotionsConsent,
    private val analytics: Analytics,
    clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)

    private val _isExplainingAlwaysLocation = MutableStateFlow(false)

    val isExplainingAlwaysLocation: StateFlow<Boolean> = _isExplainingAlwaysLocation

    private data class Restore(val isRestoring: Boolean = false, val result: RestoreResult? = null)

    private val restore = MutableStateFlow(Restore())

    private val pro = combine(
        entitlements.subscription,
        syncStateRepository.observe(),
        membershipRepository.observeAll(),
        authenticator.session,
        restore,
    ) { subscription, syncState, memberships, session, restore ->
        ProUiState(
            isPro = Entitlements.isPro(subscription != null, syncState.plan),
            hasEntitlement = subscription != null,
            isMember = memberships.firstOrNull { it.userId == session?.userId }?.role == MemberRole.MEMBER,
            details = ProDetail.details(subscription, syncState.plan),
            isRestoring = restore.isRestoring,
            restoreResult = restore.result,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.data,
        permissions.observe(),
        authenticator.session,
        pro,
        promotionsConsent.isOn,
    ) { stored, granted, session, pro, promotions ->
        SettingsUiState(
            isLoading = false,
            isSignedIn = session != null,
            location = granted.location,
            notifications = granted.notifications,
            promotionsConsent = promotions,
            defaultRadiusMeters = stored.defaultRadiusMeters,
            pro = pro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun upgrade() = paywallRequests.request(PaywallTrigger.SETTINGS)

    fun restorePurchases() {
        viewModelScope.launch {
            restore.value = Restore(isRestoring = true)
            val result = try {
                entitlements.restore()
                if (entitlements.hasEntitlement) RestoreResult.RESTORED else RestoreResult.NOTHING
            } catch (e: Exception) {
                Log.w(TAG, "Restore failed", e)
                RestoreResult.FAILED
            }
            restore.value = Restore(result = result)
        }
    }

    fun dismissRestoreResult() {
        restore.value = Restore()
    }

    fun refreshPermissions() = permissions.refresh()

    fun permissionActionTapped(kind: PermissionKind, action: PermissionAction) = analytics.logPermissionAction(kind, action)

    // Counted as a request: the sheet is how this screen asks for "all the time".
    fun explainAlwaysLocation() {
        analytics.logPermissionAction(PermissionKind.LOCATION, PermissionAction.REQUEST)
        alwaysPrompt.shown()
        _isExplainingAlwaysLocation.value = true
    }

    fun alwaysLocationAnswered(answer: AlwaysPromptAnswer) = alwaysPrompt.answered(answer)

    fun dismissAlwaysLocation() {
        _isExplainingAlwaysLocation.value = false
        permissions.refresh()
    }

    fun locationRequested() {
        viewModelScope.launch {
            permissions.markLocationRequested()
            permissions.refresh()
        }
    }

    fun notificationsRequested() {
        viewModelScope.launch {
            permissions.markNotificationsRequested()
            permissions.refresh()
        }
    }

    fun setPromotionsConsent(isOn: Boolean) {
        viewModelScope.launch {
            promotionsConsent.set(isOn, PromotionsConsent.Source.SETTINGS)
        }
    }

    fun setDefaultRadius(meters: Double) {
        viewModelScope.launch {
            preferences.setDefaultRadiusMeters(meters)
        }
    }

    private companion object {
        const val TAG = "Billing"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
