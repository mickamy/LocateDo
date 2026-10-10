package com.locatedo.locatedo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AlwaysPromptTracker
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.InstallDate
import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import com.locatedo.locatedo.core.appstatus.AppStatusStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.billing.paywallTrigger
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.common.TodosRequests
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.push.PromotionsConsent
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.core.sync.SyncEngine
import com.locatedo.locatedo.feature.onboarding.ReminderSetup
import com.locatedo.locatedo.feature.onboarding.ReminderSetupRequest
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
    val reminderSetup: ReminderSetupRequest? = null,
    val isAskingPromotions: Boolean = false,
    val notice: AppNotice? = null,
)

// What sits above the tabs: the onboarding gate, the reminder setup and promotions prompts, the notices,
// and what the app status takes away or announces.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    private val selectionRequests: PlaceSelectionRequests,
    private val inviteRequests: InviteRequests,
    private val paywallRequests: PaywallRequests,
    private val todosRequests: TodosRequests,
    sync: SyncEngine,
    authenticator: Authenticator,
    private val appStatus: AppStatusStore,
    private val promotionsConsent: PromotionsConsent,
    analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)

    private val reminderSetup = MutableStateFlow<ReminderSetupRequest?>(null)

    private val isAskingPromotions = MutableStateFlow(false)

    private var promotionsAskedAt: Instant? = null

    // A place asked for from a notification tap; the tabs open its detail on the home tab.
    val pendingPlace: StateFlow<UUID?> = selectionRequests.pending

    // An invite link opened from outside; the tabs open the join screen with it.
    val pendingInvite: StateFlow<String?> = inviteRequests.pending

    // Some screen hit a free limit, or the server refused a queued write; either way the paywall explains.
    val pendingPaywall: StateFlow<PaywallTrigger?> = paywallRequests.pending

    // A tapped completion notice; the tabs switch to the to-do list.
    val pendingTodos: StateFlow<Boolean> = todosRequests.pending

    val currentPermissions: StateFlow<Permissions?> =
        permissions.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val uiState: StateFlow<AppUiState> = combine(
        preferences.data,
        reminderSetup,
        isAskingPromotions,
        authenticator.session,
    ) { stored, setup, askingPromotions, session ->
        AppUiState(
            isLoading = false,
            hasCompletedOnboarding = stored.hasCompletedOnboarding,
            isSignedIn = session != null,
            reminderSetup = setup,
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
        viewModelScope.launch {
            combine(reminderSetup, permissions.observe()) { setup, current -> setup != null && ReminderSetup.missing(current).isEmpty() }
                .filter { it }
                .collect { closeReminderSetup(AlwaysPromptAnswer.ALLOW) }
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
            reminderSetup.value != null ||
            pendingPlace.value != null ||
            pendingInvite.value != null ||
            pendingPaywall.value != null ||
            pendingTodos.value ||
            status.requiresUpdate ||
            status.pendingNotice != null
    }

    fun placeAdded() {
        viewModelScope.launch {
            permissions.refresh()
            val stored = preferences.data.first()
            val current = permissions.observe().first()
            val now = clock.instant()
            if (!ReminderSetup.isDue(current, stored.reminderSetupShownAt, stored.reminderSetupNever, now)) {
                return@launch
            }
            val missing = ReminderSetup.missing(current)
            if (missing.isEmpty()) {
                return@launch
            }
            val shownCount = preferences.recordReminderSetupShown(now)
            alwaysPrompt.shown(
                mapOf(
                    AnalyticsParameter.MISSING to ReminderSetup.analyticsValue(missing),
                    AnalyticsParameter.SHOWN_COUNT to shownCount,
                ),
            )
            reminderSetup.value = ReminderSetupRequest(missing, shownCount)
        }
    }

    fun refreshPermissions() = permissions.refresh()

    fun preciseLocationRequested() {
        viewModelScope.launch {
            permissions.markPreciseLocationRequested()
            permissions.refresh()
        }
    }

    fun notificationsRequested() {
        viewModelScope.launch {
            permissions.markNotificationsRequested()
            permissions.refresh()
        }
    }

    // "allow" whenever nothing is missing by the time the sheet closes, however it was closed.
    fun closeReminderSetup(answer: AlwaysPromptAnswer) {
        if (reminderSetup.value == null) {
            return
        }
        reminderSetup.value = null
        viewModelScope.launch {
            if (answer == AlwaysPromptAnswer.NEVER) {
                preferences.setReminderSetupNever()
            }
            val isComplete = ReminderSetup.missing(permissions.observe().first()).isEmpty()
            alwaysPrompt.answered(if (isComplete) AlwaysPromptAnswer.ALLOW else answer)
        }
    }

    fun dismissNotice() {
        viewModelScope.launch {
            preferences.setPendingRemovedNotice(false)
            preferences.setPendingSessionEndedNotice(false)
        }
    }

    fun placeConsumed(placeId: UUID) = selectionRequests.consume(placeId)

    fun inviteConsumed(token: String) = inviteRequests.consume(token)

    fun paywallConsumed(trigger: PaywallTrigger) = paywallRequests.consume(trigger)

    fun todosConsumed() = todosRequests.consume()

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
