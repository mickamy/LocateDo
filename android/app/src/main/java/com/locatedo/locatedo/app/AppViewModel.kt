package com.locatedo.locatedo.app

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
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.data.TodoUndo
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.Todo
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.push.PromotionsConsent
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.core.sync.SyncEngine
import com.locatedo.locatedo.logic.ReminderSetup
import com.locatedo.locatedo.logic.ReminderSetupRequest
import com.locatedo.locatedo.screens.onboarding.OnboardingChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

// What opens over Home once onboarding is gone.
enum class AfterOnboarding {
    INVITE,
    ACCOUNT,
}

data class AppUiState(
    val isLoading: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val isSignedIn: Boolean = false,
    val notice: AppNotice? = null,
)

// What the app holds above any one screen: the onboarding gate, the notices, the reminder setup and news prompts,
// and the requests that come from outside the screens (a notification, a link, a refused write).
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    private val placeRequests: PlaceSelectionRequests,
    private val inviteRequests: InviteRequests,
    private val paywallRequests: PaywallRequests,
    private val todosRequests: TodosRequests,
    sync: SyncEngine,
    authenticator: Authenticator,
    private val appStatus: AppStatusStore,
    private val promotionsConsent: PromotionsConsent,
    private val undo: TodoUndo,
    private val todos: TodoRepository,
    analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {
    private val alwaysPrompt = AlwaysPromptTracker(analytics, clock)
    private val _reminderSetup = MutableStateFlow<ReminderSetupRequest?>(null)
    private val _isAskingPromotions = MutableStateFlow(false)
    private val _afterOnboarding = MutableStateFlow<AfterOnboarding?>(null)
    private var promotionsAskedAt: Instant? = null
    // Whether a sheet or dialog is up, which the news prompt does not interrupt.
    private var isPresenting = false

    val pendingPlace: StateFlow<UUID?> = placeRequests.pending
    val pendingInvite: StateFlow<String?> = inviteRequests.pending
    val pendingPaywall: StateFlow<PaywallTrigger?> = paywallRequests.pending
    val pendingTodos: StateFlow<Boolean> = todosRequests.pending
    val afterOnboarding: StateFlow<AfterOnboarding?> = _afterOnboarding
    val reminderSetup: StateFlow<ReminderSetupRequest?> = _reminderSetup
    val isAskingPromotions: StateFlow<Boolean> = _isAskingPromotions

    // A deleted to-do from any screen, offered back for a few seconds.
    val undoOffers: SharedFlow<List<Todo>> = undo.offers

    val currentPermissions: StateFlow<Permissions?> =
        permissions.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val uiState: StateFlow<AppUiState> = combine(preferences.data, authenticator.session) { stored, session ->
        AppUiState(
            isLoading = false,
            hasCompletedOnboarding = stored.hasCompletedOnboarding,
            isSignedIn = session != null,
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
        // Granted from the system page while the sheet waits: it closes as answered.
        viewModelScope.launch {
            combine(_reminderSetup, permissions.observe()) { setup, current -> setup != null && ReminderSetup.missing(current).isEmpty() }
                .filter { it }
                .collect { closeReminderSetup(AlwaysPromptAnswer.ALLOW) }
        }
    }

    fun presentingChanged(isPresenting: Boolean) {
        this.isPresenting = isPresenting
    }

    // After a new place is saved, from wherever: what reminders still need, at most once a week.
    fun placeAdded(placeName: String) {
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
            _reminderSetup.value = ReminderSetupRequest(missing, shownCount, placeName)
        }
    }

    // What was chosen is set up before Home shows: the place saved gets its reminder setup, and an invite from a link
    // is already waiting there.
    fun onboardingFinished(choice: OnboardingChoice, placeName: String?) {
        viewModelScope.launch {
            when (choice) {
                OnboardingChoice.INVITE -> if (pendingInvite.value == null) _afterOnboarding.value = AfterOnboarding.INVITE
                OnboardingChoice.SIGN_IN -> _afterOnboarding.value = AfterOnboarding.ACCOUNT
                OnboardingChoice.ADD_PLACE, OnboardingChoice.LATER -> Unit
            }
            if (placeName != null) {
                placeAdded(placeName)
            }
            preferences.setCompletedOnboarding(true)
        }
    }

    fun afterOnboardingConsumed() {
        _afterOnboarding.value = null
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
        if (_reminderSetup.value == null) {
            return
        }
        _reminderSetup.value = null
        viewModelScope.launch {
            if (answer == AlwaysPromptAnswer.NEVER) {
                preferences.setReminderSetupNever()
            }
            val isComplete = ReminderSetup.missing(permissions.observe().first()).isEmpty()
            alwaysPrompt.answered(if (isComplete) AlwaysPromptAnswer.ALLOW else answer)
        }
    }

    fun answerPromotions(answer: PromotionsConsent.Answer) {
        if (!_isAskingPromotions.value) {
            return
        }
        _isAskingPromotions.value = false
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
        _isAskingPromotions.value = true
    }

    private suspend fun isShowingSomethingElse(): Boolean {
        val stored = preferences.data.first()
        val status = appStatus.state.value
        return isPresenting ||
            !stored.hasCompletedOnboarding ||
            stored.hasPendingRemovedNotice ||
            stored.hasPendingSessionEndedNotice ||
            _reminderSetup.value != null ||
            pendingPlace.value != null ||
            pendingInvite.value != null ||
            pendingPaywall.value != null ||
            pendingTodos.value ||
            _afterOnboarding.value != null ||
            status.requiresUpdate ||
            status.pendingNotice != null
    }

    fun restore(deleted: List<Todo>) {
        viewModelScope.launch { todos.restore(deleted) }
    }

    fun dismissNotice() {
        viewModelScope.launch {
            preferences.setPendingRemovedNotice(false)
            preferences.setPendingSessionEndedNotice(false)
        }
    }

    fun placeConsumed(placeId: UUID) = placeRequests.consume(placeId)

    fun inviteConsumed(token: String) = inviteRequests.consume(token)

    fun paywallConsumed(trigger: PaywallTrigger) = paywallRequests.consume(trigger)

    fun todosConsumed() = todosRequests.consume()

    fun dismissMaintenanceBanner() {
        viewModelScope.launch { appStatus.dismissUpcomingBanner() }
    }

    fun noticeShown(notice: AppStatusDocument.Notice) {
        viewModelScope.launch { appStatus.markNoticeShown(notice) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
