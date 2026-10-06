package com.locatedo.locatedo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.locatedo.locatedo.core.model.Place
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
    }

    // Device state rather than a preference: what the app last handed to the geofencing client (see GeofenceRecord).
    val registeredGeofences: Flow<String> = dataStore.data.map { it[Keys.registeredGeofences] ?: "" }

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
