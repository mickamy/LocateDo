package com.locatedo.locatedo.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.analyticsKey
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class OnboardingStep(val key: String) {
    INTRO("intro"),
    PRIVACY("privacy"),
    NOTIFICATIONS("notifications"),
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val permissions: PermissionsRepository,
    private val analytics: Analytics,
    private val clock: Clock,
) : ViewModel() {
    private val _step = MutableStateFlow(OnboardingStep.INTRO)
    private val startedAt = clock.instant()

    val step: StateFlow<OnboardingStep> = _step

    // The intro leads to the privacy page, which asks for location.
    fun start() {
        _step.value = OnboardingStep.PRIVACY
    }

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

    private suspend fun finish() {
        val granted = permissions.observe().first()
        analytics.log(
            AnalyticsEvent.ONBOARDING_COMPLETED,
            mapOf(
                AnalyticsParameter.LOCATION_AUTH to granted.location.analyticsKey,
                AnalyticsParameter.NOTIFICATION_AUTH to granted.notifications.analyticsKey,
                AnalyticsParameter.DURATION_S to Duration.between(startedAt, clock.instant()).seconds.coerceAtLeast(0),
            ),
        )
        preferences.setCompletedOnboarding(true)
    }
}
