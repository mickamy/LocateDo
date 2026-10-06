package com.locatedo.locatedo.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class OnboardingStep { INTRO, NOTIFICATIONS }

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
) : ViewModel() {
    private val _step = MutableStateFlow(OnboardingStep.INTRO)

    val step: StateFlow<OnboardingStep> = _step

    // Called once the system's location dialog closes, whatever the user chose.
    fun locationRequested() {
        viewModelScope.launch {
            permissions.markLocationRequested()
            if (permissions.observe().first().notifications == NotificationAuth.NOT_DETERMINED) {
                _step.value = OnboardingStep.NOTIFICATIONS
            } else {
                finish()
            }
        }
    }

    fun notificationsRequested() {
        viewModelScope.launch {
            permissions.markNotificationsRequested()
            finish()
        }
    }

    fun skipNotifications() {
        viewModelScope.launch {
            finish()
        }
    }

    private suspend fun finish() = preferences.setCompletedOnboarding(true)
}
