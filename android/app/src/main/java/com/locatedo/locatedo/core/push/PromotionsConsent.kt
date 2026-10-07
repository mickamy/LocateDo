package com.locatedo.locatedo.core.push

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsUserProperty
import com.locatedo.locatedo.core.analytics.analyticsKey
import com.locatedo.locatedo.core.common.di.ApplicationScope
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.NotificationAuth
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Consent to promotional pushes, asked once after the first arrival notification and switchable in Settings.
@Singleton
class PromotionsConsent @Inject constructor(
    private val preferences: AppPreferences,
    private val registration: DeviceRegistration,
    private val analytics: Analytics,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    enum class Source(val key: String) {
        FIRST_ARRIVAL("first_arrival"),
        SETTINGS("settings"),
    }

    enum class Answer(val key: String) {
        ACCEPTED("accepted"),
        DECLINED("declined"),
        DISMISSED("dismissed"),
    }

    val isOn: Flow<Boolean> = preferences.promotions.map { it.consent }

    // The server learns of the change on the application scope, so leaving the screen does not cancel it.
    suspend fun set(isOn: Boolean, source: Source): Job? {
        if (preferences.promotions.first().consent == isOn) {
            return null
        }
        preferences.setPromotionsConsent(isOn)
        var value = "off"
        var flag = "0"
        if (isOn) {
            value = "on"
            flag = "1"
        }
        analytics.log(
            AnalyticsEvent.PROMOTIONS_CONSENT_CHANGED,
            mapOf(AnalyticsParameter.TO to value, AnalyticsParameter.SOURCE to source.key),
        )
        analytics.setUserProperty(AnalyticsUserProperty.PROMOTIONS_CONSENT, flag)
        return scope.launch { registration.consentChanged() }
    }

    suspend fun arrivalNotified() {
        preferences.setReceivedArrivalNotification()
    }

    suspend fun shouldPrompt(notificationAuth: NotificationAuth, lastArrivalOpenedAt: Instant?, now: Instant): Boolean {
        val record = preferences.promotions.first()
        if (!record.hasReceivedArrivalNotification || record.hasShownPrompt || record.consent) {
            return false
        }
        if (notificationAuth != NotificationAuth.AUTHORIZED) {
            return false
        }
        if (lastArrivalOpenedAt != null && Duration.between(lastArrivalOpenedAt, now) < QUIET_PERIOD_AFTER_ARRIVAL_OPENED) {
            return false
        }
        return true
    }

    suspend fun promptShown(daysSinceInstall: Int, notificationAuth: NotificationAuth) {
        preferences.setShownPromotionsPrompt()
        analytics.log(
            AnalyticsEvent.PROMOTIONS_PROMPT_SHOWN,
            mapOf(
                AnalyticsParameter.DAYS_SINCE_INSTALL to daysSinceInstall,
                AnalyticsParameter.NOTIFICATION_AUTH to notificationAuth.analyticsKey,
            ),
        )
    }

    suspend fun answerPrompt(answer: Answer, duration: Duration): Job? {
        analytics.log(
            AnalyticsEvent.PROMOTIONS_PROMPT_ANSWERED,
            mapOf(
                AnalyticsParameter.RESULT to answer.key,
                AnalyticsParameter.DURATION_S to duration.seconds.coerceAtLeast(0),
            ),
        )
        if (answer != Answer.ACCEPTED) {
            return null
        }
        return set(true, Source.FIRST_ARRIVAL)
    }

    companion object {
        val QUIET_PERIOD_AFTER_ARRIVAL_OPENED: Duration = Duration.ofMinutes(30)
    }
}
