package com.locatedo.locatedo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.locatedo.locatedo.core.model.Place
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val hasRequestedLocation: Boolean = false,
    val hasRequestedNotifications: Boolean = false,
    val hasPromptedAlwaysLocation: Boolean = false,
    val defaultRadiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
    val hasPendingRemovedNotice: Boolean = false,
    val hasPendingSessionEndedNotice: Boolean = false,
)

// What the analytics reporting remembers between launches; it survives sign-out and account deletion.
data class AnalyticsRecord(
    val firstLaunchedAt: Instant? = null,
    val dailyStateReportedOn: String? = null,
    val lastReportedLocationAuth: String? = null,
)

// The last app status read, as served, plus what the user has already seen of it.
data class AppStatusRecord(
    val document: String? = null,
    val dismissedMaintenance: String? = null,
    val shownNotices: Set<String> = emptySet(),
)

@Singleton
class AppPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val completedOnboarding = booleanPreferencesKey("completedOnboarding")
        val requestedLocation = booleanPreferencesKey("requestedLocation")
        val requestedNotifications = booleanPreferencesKey("requestedNotifications")
        val promptedAlwaysLocation = booleanPreferencesKey("promptedAlwaysLocation")
        val defaultRadiusMeters = doublePreferencesKey("defaultRadiusMeters")
        val registeredGeofences = stringPreferencesKey("registeredGeofences")
        val pendingRemovedNotice = booleanPreferencesKey("pendingRemovedNotice")
        val pendingSessionEndedNotice = booleanPreferencesKey("pendingSessionEndedNotice")
        val firstLaunchedAt = longPreferencesKey("firstLaunchedAt")
        val dailyStateReportedOn = stringPreferencesKey("dailyStateReportedOn")
        val lastReportedLocationAuth = stringPreferencesKey("lastReportedLocationAuth")
        val appStatusDocument = stringPreferencesKey("appStatusDocument")
        val appStatusDismissedMaintenance = stringPreferencesKey("appStatusDismissedMaintenance")
        val appStatusShownNotices = stringSetPreferencesKey("appStatusShownNotices")
    }

    // Device state rather than a preference: what the app last handed to the geofencing client (see GeofenceRecord).
    val registeredGeofences: Flow<String> = dataStore.data.map { it[Keys.registeredGeofences] ?: "" }

    val analytics: Flow<AnalyticsRecord> = dataStore.data.map { preferences ->
        AnalyticsRecord(
            firstLaunchedAt = preferences[Keys.firstLaunchedAt]?.let(Instant::ofEpochMilli),
            dailyStateReportedOn = preferences[Keys.dailyStateReportedOn],
            lastReportedLocationAuth = preferences[Keys.lastReportedLocationAuth],
        )
    }

    val appStatus: Flow<AppStatusRecord> = dataStore.data.map { preferences ->
        AppStatusRecord(
            document = preferences[Keys.appStatusDocument],
            dismissedMaintenance = preferences[Keys.appStatusDismissedMaintenance],
            shownNotices = preferences[Keys.appStatusShownNotices] ?: emptySet(),
        )
    }

    val data: Flow<UserPreferences> = dataStore.data.map { preferences ->
        val radius = preferences[Keys.defaultRadiusMeters]
        UserPreferences(
            hasCompletedOnboarding = preferences[Keys.completedOnboarding] ?: false,
            hasRequestedLocation = preferences[Keys.requestedLocation] ?: false,
            hasRequestedNotifications = preferences[Keys.requestedNotifications] ?: false,
            hasPromptedAlwaysLocation = preferences[Keys.promptedAlwaysLocation] ?: false,
            defaultRadiusMeters = if (radius != null && radius in Place.RADIUS_RANGE) {
                radius
            } else {
                Place.DEFAULT_RADIUS_METERS
            },
            hasPendingRemovedNotice = preferences[Keys.pendingRemovedNotice] ?: false,
            hasPendingSessionEndedNotice = preferences[Keys.pendingSessionEndedNotice] ?: false,
        )
    }

    suspend fun setCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[Keys.completedOnboarding] = completed }
    }

    suspend fun setRequestedLocation(requested: Boolean) {
        dataStore.edit { it[Keys.requestedLocation] = requested }
    }

    suspend fun setRequestedNotifications(requested: Boolean) {
        dataStore.edit { it[Keys.requestedNotifications] = requested }
    }

    suspend fun setPromptedAlwaysLocation(prompted: Boolean) {
        dataStore.edit { it[Keys.promptedAlwaysLocation] = prompted }
    }

    suspend fun setDefaultRadiusMeters(meters: Double) {
        dataStore.edit { it[Keys.defaultRadiusMeters] = meters }
    }

    suspend fun setRegisteredGeofences(encoded: String) {
        dataStore.edit { it[Keys.registeredGeofences] = encoded }
    }

    // Set when local data was wiped behind the user's back; the next screen explains why, then clears it.
    suspend fun setPendingRemovedNotice(pending: Boolean) {
        dataStore.edit { it[Keys.pendingRemovedNotice] = pending }
    }

    suspend fun setPendingSessionEndedNotice(pending: Boolean) {
        dataStore.edit { it[Keys.pendingSessionEndedNotice] = pending }
    }

    suspend fun recordFirstLaunch(now: Instant) {
        dataStore.edit {
            if (it[Keys.firstLaunchedAt] == null) {
                it[Keys.firstLaunchedAt] = now.toEpochMilli()
            }
        }
    }

    suspend fun setDailyStateReportedOn(day: String) {
        dataStore.edit { it[Keys.dailyStateReportedOn] = day }
    }

    suspend fun setLastReportedLocationAuth(key: String) {
        dataStore.edit { it[Keys.lastReportedLocationAuth] = key }
    }

    suspend fun setAppStatusDocument(json: String) {
        dataStore.edit { it[Keys.appStatusDocument] = json }
    }

    suspend fun setDismissedMaintenance(key: String) {
        dataStore.edit { it[Keys.appStatusDismissedMaintenance] = key }
    }

    suspend fun setShownNotices(ids: Set<String>) {
        dataStore.edit { it[Keys.appStatusShownNotices] = ids }
    }

    suspend fun reset() {
        dataStore.edit {
            it.remove(Keys.completedOnboarding)
            it.remove(Keys.requestedLocation)
            it.remove(Keys.requestedNotifications)
            it.remove(Keys.promptedAlwaysLocation)
            it.remove(Keys.defaultRadiusMeters)
        }
    }
}
