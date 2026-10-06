package com.locatedo.locatedo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val location: LocationAuth = LocationAuth.NOT_DETERMINED,
    val notifications: NotificationAuth = NotificationAuth.NOT_DETERMINED,
    val defaultRadiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    authenticator: Authenticator,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.data,
        permissions.observe(),
        authenticator.session,
    ) { stored, granted, session ->
        SettingsUiState(
            isLoading = false,
            isSignedIn = session != null,
            location = granted.location,
            notifications = granted.notifications,
            defaultRadiusMeters = stored.defaultRadiusMeters,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    fun refreshPermissions() = permissions.refresh()

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

    fun setDefaultRadius(meters: Double) {
        viewModelScope.launch {
            preferences.setDefaultRadiusMeters(meters)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
