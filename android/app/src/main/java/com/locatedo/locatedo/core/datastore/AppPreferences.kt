package com.locatedo.locatedo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val hasRequestedPreciseLocation: Boolean = false,
    val reminderSetupShownAt: Instant? = null,
    val reminderSetupShownCount: Int = 0,
    val reminderSetupNever: Boolean = false,
    val defaultRadiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
    val hasPendingRemovedNotice: Boolean = false,
    val hasPendingSessionEndedNotice: Boolean = false,
)

// What the analytics reporting remembers between launches; it survives sign-out and account deletion.
data class AnalyticsRecord(
    val firstLaunchedAt: Instant? = null,
    val dailyStateReportedOn: String? = null,
    val lastReportedLocationAuth: String? = null,
    val lastReportedNotificationAuth: String? = null,
)

// The last app status read, as served, plus what the user has already seen of it.
data class AppStatusRecord(
    val document: String? = null,
    val dismissedMaintenance: String? = null,
    val shownNotices: Set<String> = emptySet(),
)

// Promotional push consent belongs to the device, not the account, so it survives sign-out and account deletion.
data class PromotionsRecord(
    val consent: Boolean = false,
    val hasReceivedArrivalNotification: Boolean = false,
    val hasShownPrompt: Boolean = false,
)

// Consent to usage analytics and crash reports in the EEA and the UK, per device. storeCountryOverride is set only from
// the debug tools.
data class AnalyticsConsentRecord(
    val answer: Boolean? = null,
    val required: Boolean? = null,
    val storeCountryOverride: String? = null,
)

@Singleton
class AppPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val completedOnboarding = booleanPreferencesKey("completedOnboarding")
        val requestedLocation = booleanPreferencesKey("requestedLocation")
        val requestedNotifications = booleanPreferencesKey("requestedNotifications")
        val requestedPreciseLocation = booleanPreferencesKey("requestedPreciseLocation")
        val reminderSetupShownAt = longPreferencesKey("reminderSetupShownAt")
        val reminderSetupShownCount = intPreferencesKey("reminderSetupShownCount")
        val reminderSetupNever = booleanPreferencesKey("reminderSetupNever")
        val defaultRadiusMeters = doublePreferencesKey("defaultRadiusMeters")
        val registeredGeofences = stringPreferencesKey("registeredGeofences")
        val pendingRemovedNotice = booleanPreferencesKey("pendingRemovedNotice")
        val pendingSessionEndedNotice = booleanPreferencesKey("pendingSessionEndedNotice")
        val firstLaunchedAt = longPreferencesKey("firstLaunchedAt")
        val dailyStateReportedOn = stringPreferencesKey("dailyStateReportedOn")
        val lastReportedLocationAuth = stringPreferencesKey("lastReportedLocationAuth")
        val lastReportedNotificationAuth = stringPreferencesKey("lastReportedNotificationAuth")
        val appStatusDocument = stringPreferencesKey("appStatusDocument")
        val appStatusDismissedMaintenance = stringPreferencesKey("appStatusDismissedMaintenance")
        val appStatusShownNotices = stringSetPreferencesKey("appStatusShownNotices")
        val promotionsConsent = booleanPreferencesKey("promotionsConsent")
        val receivedArrivalNotification = booleanPreferencesKey("receivedArrivalNotification")
        val shownPromotionsPrompt = booleanPreferencesKey("shownPromotionsPrompt")
        val completionNotices = booleanPreferencesKey("completionNotices")
        val analyticsConsent = booleanPreferencesKey("analyticsConsent")
        val analyticsConsentRequired = booleanPreferencesKey("analyticsConsentRequired")
        val consentStoreCountry = stringPreferencesKey("consentStoreCountry")
    }

    // Device state rather than a preference: what the app last handed to the geofencing client (see GeofenceRecord).
    val registeredGeofences: Flow<String> = dataStore.data.map { it[Keys.registeredGeofences] ?: "" }

    val analytics: Flow<AnalyticsRecord> = dataStore.data.map { preferences ->
        AnalyticsRecord(
            firstLaunchedAt = preferences[Keys.firstLaunchedAt]?.let(Instant::ofEpochMilli),
            dailyStateReportedOn = preferences[Keys.dailyStateReportedOn],
            lastReportedLocationAuth = preferences[Keys.lastReportedLocationAuth],
            lastReportedNotificationAuth = preferences[Keys.lastReportedNotificationAuth],
        )
    }

    val appStatus: Flow<AppStatusRecord> = dataStore.data.map { preferences ->
        AppStatusRecord(
            document = preferences[Keys.appStatusDocument],
            dismissedMaintenance = preferences[Keys.appStatusDismissedMaintenance],
            shownNotices = preferences[Keys.appStatusShownNotices] ?: emptySet(),
        )
    }

    val promotions: Flow<PromotionsRecord> = dataStore.data.map { preferences ->
        PromotionsRecord(
            consent = preferences[Keys.promotionsConsent] ?: false,
            hasReceivedArrivalNotification = preferences[Keys.receivedArrivalNotification] ?: false,
            hasShownPrompt = preferences[Keys.shownPromotionsPrompt] ?: false,
        )
    }

    val analyticsConsent: Flow<AnalyticsConsentRecord> = dataStore.data.map { preferences ->
        AnalyticsConsentRecord(
            answer = preferences[Keys.analyticsConsent],
            required = preferences[Keys.analyticsConsentRequired],
            storeCountryOverride = preferences[Keys.consentStoreCountry],
        )
    }

    // Per device, like the promotions consent.
    val completionNotices: Flow<Boolean> = dataStore.data.map { it[Keys.completionNotices] ?: true }

    val data: Flow<UserPreferences> = dataStore.data.map { preferences ->
        val radius = preferences[Keys.defaultRadiusMeters]
        UserPreferences(
            hasCompletedOnboarding = preferences[Keys.completedOnboarding] ?: false,
            hasRequestedLocation = preferences[Keys.requestedLocation] ?: false,
            hasRequestedNotifications = preferences[Keys.requestedNotifications] ?: false,
            hasRequestedPreciseLocation = preferences[Keys.requestedPreciseLocation] ?: false,
            reminderSetupShownAt = preferences[Keys.reminderSetupShownAt]?.let(Instant::ofEpochMilli),
            reminderSetupShownCount = preferences[Keys.reminderSetupShownCount] ?: 0,
            reminderSetupNever = preferences[Keys.reminderSetupNever] ?: false,
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

    suspend fun setRequestedPreciseLocation(requested: Boolean) {
        dataStore.edit { it[Keys.requestedPreciseLocation] = requested }
    }

    // Returns which showing this is, counting from 1.
    suspend fun recordReminderSetupShown(at: Instant): Int {
        val stored = dataStore.edit {
            it[Keys.reminderSetupShownAt] = at.toEpochMilli()
            it[Keys.reminderSetupShownCount] = (it[Keys.reminderSetupShownCount] ?: 0) + 1
        }
        return stored[Keys.reminderSetupShownCount] ?: 1
    }

    suspend fun setReminderSetupNever() {
        dataStore.edit { it[Keys.reminderSetupNever] = true }
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

    suspend fun setLastReportedNotificationAuth(key: String) {
        dataStore.edit { it[Keys.lastReportedNotificationAuth] = key }
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

    suspend fun setPromotionsConsent(consent: Boolean) {
        dataStore.edit { it[Keys.promotionsConsent] = consent }
    }

    suspend fun setReceivedArrivalNotification() {
        dataStore.edit { it[Keys.receivedArrivalNotification] = true }
    }

    suspend fun setShownPromotionsPrompt() {
        dataStore.edit { it[Keys.shownPromotionsPrompt] = true }
    }

    suspend fun setCompletionNotices(isOn: Boolean) {
        dataStore.edit { it[Keys.completionNotices] = isOn }
    }

    suspend fun setAnalyticsConsent(answer: Boolean) {
        dataStore.edit { it[Keys.analyticsConsent] = answer }
    }

    suspend fun setAnalyticsConsentRequired(required: Boolean) {
        dataStore.edit { it[Keys.analyticsConsentRequired] = required }
    }

    // Starts the consent over, so the debug tools can try another region from a fresh install's state.
    suspend fun setConsentStoreCountryOverride(country: String?) {
        dataStore.edit {
            if (country == null) {
                it.remove(Keys.consentStoreCountry)
            } else {
                it[Keys.consentStoreCountry] = country
            }
            it.remove(Keys.analyticsConsent)
            it.remove(Keys.analyticsConsentRequired)
        }
    }

    suspend fun reset() {
        dataStore.edit {
            it.remove(Keys.completedOnboarding)
            it.remove(Keys.requestedLocation)
            it.remove(Keys.requestedNotifications)
            it.remove(Keys.requestedPreciseLocation)
            it.remove(Keys.reminderSetupShownAt)
            it.remove(Keys.reminderSetupShownCount)
            it.remove(Keys.reminderSetupNever)
            it.remove(Keys.defaultRadiusMeters)
        }
    }
}
