package com.locatedo.locatedo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val isLoading: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val isExplainingAlwaysLocation: Boolean = false,
)

// What sits above the tabs: the onboarding gate and the one-time "always" location prompt.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
) : ViewModel() {
    private val isExplainingAlwaysLocation = MutableStateFlow(false)

    val uiState: StateFlow<AppUiState> = combine(preferences.data, isExplainingAlwaysLocation) { stored, explaining ->
        AppUiState(
            isLoading = false,
            hasCompletedOnboarding = stored.hasCompletedOnboarding,
            isExplainingAlwaysLocation = explaining,
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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
