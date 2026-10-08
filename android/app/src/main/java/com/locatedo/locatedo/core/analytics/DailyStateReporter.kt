package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

// Run on every return to the foreground: user properties every time, the daily_state event once a day.
@Singleton
class DailyStateReporter @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val membershipRepository: MembershipRepository,
    private val syncStateRepository: SyncStateRepository,
    private val entitlements: Entitlements,
    private val authenticator: Authenticator,
    private val permissions: PermissionsRepository,
    private val locationRepository: LocationRepository,
    private val preferences: AppPreferences,
    private val analytics: Analytics,
    private val clock: Clock,
) {
    suspend fun report() {
        permissions.refresh()
        val now = clock.instant()
        val record = preferences.analytics.first()
        val granted = permissions.observe().first()
        val state = DailyState(
            counts = DailyState.counts(
                places = placeRepository.observeAllWithTodos().first(),
                categories = categoryRepository.observeAll().first(),
                members = membershipRepository.observeAll().first(),
                now = now,
            ),
            daysSinceInstall = InstallDate.daysSinceInstall(record.firstLaunchedAt, now),
            plan = DailyState.plan(entitlements.subscription.value, syncStateRepository.get().plan),
            signedIn = authenticator.current() != null,
            locationAuth = granted.location,
            preciseLocation = locationRepository.hasPrecisePermission(),
            notificationAuth = granted.notifications,
            promotionsConsent = preferences.promotions.first().consent,
            batteryOptimizationExempt = permissions.isBatteryOptimizationExempt(),
        )
        LocationAuthHistory.change(record.lastReportedLocationAuth, state.locationAuth)?.let { (from, to) ->
            analytics.log(
                AnalyticsEvent.LOCATION_AUTH_CHANGED,
                mapOf(AnalyticsParameter.FROM to from.analyticsKey, AnalyticsParameter.TO to to.analyticsKey),
            )
        }
        preferences.setLastReportedLocationAuth(state.locationAuth.analyticsKey)
        NotificationAuthHistory.change(record.lastReportedNotificationAuth, state.notificationAuth)?.let { (from, to) ->
            analytics.log(
                AnalyticsEvent.NOTIFICATION_AUTH_CHANGED,
                mapOf(AnalyticsParameter.FROM to from.analyticsKey, AnalyticsParameter.TO to to.analyticsKey),
            )
        }
        preferences.setLastReportedNotificationAuth(state.notificationAuth.analyticsKey)
        for ((property, value) in state.userProperties) {
            analytics.setUserProperty(property, value)
        }
        if (DailyStateSchedule.isDue(record.dailyStateReportedOn, now)) {
            analytics.log(AnalyticsEvent.DAILY_STATE, state.parameters)
            preferences.setDailyStateReportedOn(DailyStateSchedule.day(now))
        }
    }
}
