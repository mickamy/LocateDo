package com.locatedo.locatedo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.InstallDate
import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import com.locatedo.locatedo.core.appstatus.AppStatusStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.push.PromotionsConsent
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Why local data was wiped while the user was not looking.
enum class AppNotice {
    REMOVED,
    SESSION_ENDED,
}

data class AppUiState(
    val isLoading: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val isSignedIn: Boolean = false,
    val isExplainingAlwaysLocation: Boolean = false,
    val isAskingPromotions: Boolean = false,
    val notice: AppNotice? = null,
)

// What sits above the tabs: the onboarding gate, the one-time "always" location and promotions prompts, the notices,
// and what the app status takes away or announces.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    selectionRequests: PlaceSelectionRequests,
    private val inviteRequests: InviteRequests,
    private val paywallRequests: PaywallRequests,
    sync: SyncEngine,
    authenticator: Authenticator,
    private val appStatus: AppStatusStore,
    private val promotionsConsent: PromotionsConsent,
    private val clock: Clock,
) : ViewModel() {
    private val isExplainingAlwaysLocation = MutableStateFlow(false)

    private val isAskingPromotions = MutableStateFlow(false)

    private var promotionsAskedAt: Instant? = null

    // A place asked for from outside the home tab (a notification tap); the tabs switch to home so it can be shown.
    val pendingPlace: StateFlow<UUID?> = selectionRequests.pending

    // An invite link opened from outside; the tabs open the join screen with it.
    val pendingInvite: StateFlow<String?> = inviteRequests.pending

    // Some screen hit a free limit, or the server refused a queued write; either way the paywall explains.
    val pendingPaywall: StateFlow<PaywallTrigger?> = paywallRequests.pending

    val uiState: StateFlow<AppUiState> = combine(
        preferences.data,
        isExplainingAlwaysLocation,
        isAskingPromotions,
        authenticator.session,
    ) { stored, explaining, askingPromotions, session ->
        AppUiState(
            isLoading = false,
            hasCompletedOnboarding = stored.hasCompletedOnboarding,
            isSignedIn = session != null,
            isExplainingAlwaysLocation = explaining,
            isAskingPromotions = askingPromotions,
            notice = when {
                stored.hasPendingRemovedNotice -> AppNotice.REMOVED
                stored.hasPendingSessionEndedNotice -> AppNotice.SESSION_ENDED
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppUiState())

    init {
        viewModelScope.launch {
            sync.limitRejected.collect { paywallRequests.request(it.paywallTrigger) }
        }
        viewModelScope.launch {
            promotionsConsent.promptDue.filter { it }.collect { askPromotions() }
        }
    }

    fun answerPromotions(answer: PromotionsConsent.Answer) {
        if (!isAskingPromotions.value) {
            return
        }
        isAskingPromotions.value = false
        val askedAt = promotionsAskedAt ?: return
        viewModelScope.launch {
            promotionsConsent.answerPrompt(answer, Duration.between(askedAt, clock.instant()))
        }
    }

    // Skipped, not postponed, when something else has the screen; the next return to the foreground asks again.
    private suspend fun askPromotions() {
        promotionsConsent.takePrompt()
        if (isShowingSomethingElse()) {
            return
        }
        val now = clock.instant()
        promotionsAskedAt = now
        promotionsConsent.promptShown(
            daysSinceInstall = InstallDate.daysSinceInstall(preferences.analytics.first().firstLaunchedAt, now),
            notificationAuth = permissions.observe().first().notifications,
        )
        isAskingPromotions.value = true
    }

    private suspend fun isShowingSomethingElse(): Boolean {
        val stored = preferences.data.first()
        val status = appStatus.state.value
        return !stored.hasCompletedOnboarding ||
            stored.hasPendingRemovedNotice ||
            stored.hasPendingSessionEndedNotice ||
            isExplainingAlwaysLocation.value ||
            pendingPlace.value != null ||
            pendingInvite.value != null ||
            pendingPaywall.value != null ||
            status.requiresUpdate ||
            status.pendingNotice != null
    }

    // Offered once, right after the first place is saved, while location is granted for foreground use only.
    fun placeAdded() {
        viewModelScope.launch {
            if (preferences.data.first().hasPromptedAlwaysLocation) {
                return@launch
            }
            if (permissions.observe().first().location != LocationAuth.WHEN_IN_USE) {
                return@launch
            }
            preferences.setPromptedAlwaysLocation(true)
            isExplainingAlwaysLocation.value = true
        }
    }

    fun dismissAlwaysLocation() {
        isExplainingAlwaysLocation.value = false
    }

    fun dismissNotice() {
        viewModelScope.launch {
            preferences.setPendingRemovedNotice(false)
            preferences.setPendingSessionEndedNotice(false)
        }
    }

    fun inviteConsumed(token: String) = inviteRequests.consume(token)

    fun paywallConsumed(trigger: PaywallTrigger) = paywallRequests.consume(trigger)

    fun dismissMaintenanceBanner() {
        viewModelScope.launch {
            appStatus.dismissUpcomingBanner()
        }
    }

    fun noticeShown(notice: AppStatusDocument.Notice) {
        viewModelScope.launch {
            appStatus.markNoticeShown(notice)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
