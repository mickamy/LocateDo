package com.locatedo.locatedo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.common.PlaceSelectionRequests
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.sharing.InviteRequests
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val isExplainingAlwaysLocation: Boolean = false,
    val notice: AppNotice? = null,
)

// What sits above the tabs: the onboarding gate, the one-time "always" location prompt, and the notices.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    selectionRequests: PlaceSelectionRequests,
    private val inviteRequests: InviteRequests,
    sync: SyncEngine,
) : ViewModel() {
    private val isExplainingAlwaysLocation = MutableStateFlow(false)

    // A place asked for from outside the home tab (a notification tap); the tabs switch to home so it can be shown.
    val pendingPlace: StateFlow<UUID?> = selectionRequests.pending

    // An invite link opened from outside; the tabs open the join screen with it.
    val pendingInvite: StateFlow<String?> = inviteRequests.pending

    // A queued write the server refused on the free plan; the row is already gone, the user gets told why.
    val limitRejected: Flow<FreeLimit> = sync.limitRejected

    val uiState: StateFlow<AppUiState> = combine(preferences.data, isExplainingAlwaysLocation) { stored, explaining ->
        AppUiState(
            isLoading = false,
            hasCompletedOnboarding = stored.hasCompletedOnboarding,
            isExplainingAlwaysLocation = explaining,
            notice = when {
                stored.hasPendingRemovedNotice -> AppNotice.REMOVED
                stored.hasPendingSessionEndedNotice -> AppNotice.SESSION_ENDED
                else -> null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppUiState())

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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
