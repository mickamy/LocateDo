package com.locatedo.locatedo.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsConsent
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.analyticsKey
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OnboardingChoice(val key: String) {
    ADD_PLACE("add_place"),
    LATER("later"),
    INVITE("invite"),
    SIGN_IN("sign_in"),
}

enum class OnboardingStep(val key: String) {
    INTRO("intro"),
    STORE_KIND("store_kind"),
    STORE_NAME("store_name"),
    TODOS("todos"),
    PRIVACY("privacy"),
    DONE("done"),
    RETURNING("returning"),
    ANALYTICS("analytics"),
}

data class OnboardingResult(val choice: OnboardingChoice, val placeName: String?)

// What onboarding decided across its pages: the way chosen, the first place saved, and when it ends.
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissions: PermissionsRepository,
    private val analytics: Analytics,
    private val analyticsConsent: AnalyticsConsent,
    private val clock: Clock,
) : ViewModel() {
    private val startedAt = clock.instant()
    private val _result = MutableStateFlow<OnboardingResult?>(null)
    private var choice = OnboardingChoice.LATER
    private var addedKind: StoreKind? = null
    private var addedName: String? = null
    private var todosStore: FirstStore? = null

    val result: StateFlow<OnboardingResult?> = _result

    val currentPermissions: StateFlow<Permissions?> =
        permissions.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    suspend fun needsLocation(): Boolean = permissions.observe().first().location == LocationAuth.NOT_DETERMINED

    // To-dos written for one store do not carry over to another picked after going back.
    fun startsNewTodos(store: FirstStore): Boolean {
        val isNew = store != todosStore
        todosStore = store
        return isNew
    }

    fun choose(choice: OnboardingChoice) {
        this.choice = choice
    }

    fun locationRequested() {
        viewModelScope.launch {
            permissions.markLocationRequested()
            permissions.refresh()
        }
    }

    fun firstPlaceSaved(kind: StoreKind, name: String) {
        choice = OnboardingChoice.ADD_PLACE
        addedKind = kind
        addedName = name
    }

    // True when the usage data question comes first; otherwise onboarding ends here.
    suspend fun finish(): Boolean {
        if (analyticsConsent.state.first().needsAnswer) {
            return true
        }
        complete()
        return false
    }

    // Asked last in the EEA and the UK; what was logged before the answer is sent or dropped with it.
    fun answerAnalytics(isOn: Boolean) {
        viewModelScope.launch {
            analyticsConsent.set(isOn, AnalyticsConsent.Source.ONBOARDING)
            complete()
        }
    }

    private suspend fun complete() {
        val granted = permissions.observe().first()
        val parameters = mutableMapOf<AnalyticsParameter, Any>(
            AnalyticsParameter.CHOICE to choice.key,
            AnalyticsParameter.LOCATION_AUTH to granted.location.analyticsKey,
            AnalyticsParameter.NOTIFICATION_AUTH to granted.notifications.analyticsKey,
            AnalyticsParameter.DURATION_S to Duration.between(startedAt, clock.instant()).seconds.coerceAtLeast(0),
        )
        addedKind?.let { parameters[AnalyticsParameter.KIND] = it.key }
        analytics.log(AnalyticsEvent.ONBOARDING_COMPLETED, parameters)
        _result.value = OnboardingResult(choice, addedName)
    }
}
