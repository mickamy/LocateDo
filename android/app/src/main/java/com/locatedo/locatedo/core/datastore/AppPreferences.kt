package com.locatedo.locatedo.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import com.locatedo.locatedo.core.model.Place
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val hasPromptedAlwaysLocation: Boolean = false,
    val defaultRadiusMeters: Double = Place.DEFAULT_RADIUS_METERS,
)

@Singleton
class AppPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val completedOnboarding = booleanPreferencesKey("completedOnboarding")
        val promptedAlwaysLocation = booleanPreferencesKey("promptedAlwaysLocation")
        val defaultRadiusMeters = doublePreferencesKey("defaultRadiusMeters")
    }

    val data: Flow<UserPreferences> = dataStore.data.map { preferences ->
        val radius = preferences[Keys.defaultRadiusMeters]
        UserPreferences(
            hasCompletedOnboarding = preferences[Keys.completedOnboarding] ?: false,
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

    suspend fun setPromptedAlwaysLocation(prompted: Boolean) {
        dataStore.edit { it[Keys.promptedAlwaysLocation] = prompted }
    }

    suspend fun setDefaultRadiusMeters(meters: Double) {
        dataStore.edit { it[Keys.defaultRadiusMeters] = meters }
    }

    suspend fun reset() {
        dataStore.edit {
            it.remove(Keys.completedOnboarding)
            it.remove(Keys.promptedAlwaysLocation)
            it.remove(Keys.defaultRadiusMeters)
        }
    }
}
