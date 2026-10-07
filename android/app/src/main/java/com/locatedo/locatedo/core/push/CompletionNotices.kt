package com.locatedo.locatedo.core.push

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Whether this device hears when someone in the household checks off a to-do its user added. Only signed-in
// devices are sent these, so a signed-out change waits for the registration that follows signing in.
@Singleton
class CompletionNotices @Inject constructor(
    private val preferences: AppPreferences,
    private val registration: DeviceRegistration,
    private val analytics: Analytics,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val isOn: Flow<Boolean> = preferences.completionNotices

    suspend fun set(isOn: Boolean): Job? {
        if (preferences.completionNotices.first() == isOn) {
            return null
        }
        preferences.setCompletionNotices(isOn)
        var value = "off"
        if (isOn) {
            value = "on"
        }
        analytics.log(AnalyticsEvent.COMPLETION_NOTICES_CHANGED, mapOf(AnalyticsParameter.TO to value))
        return scope.launch { registration.registerIfNeeded() }
    }
}
