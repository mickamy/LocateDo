package com.locatedo.locatedo.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun defaultsApplyBeforeAnythingIsSaved() = runTest {
        assertEquals(UserPreferences(), preferences().data.first())
    }

    @Test
    fun savesAndReadsBack() = runTest {
        val preferences = preferences()

        preferences.setCompletedOnboarding(true)
        preferences.setPromptedAlwaysLocation(true)
        preferences.setDefaultRadiusMeters(250.0)

        assertEquals(
            UserPreferences(hasCompletedOnboarding = true, hasPromptedAlwaysLocation = true, defaultRadiusMeters = 250.0),
            preferences.data.first(),
        )
    }

    @Test
    fun anOutOfRangeRadiusFallsBackToTheDefault() = runTest {
        val preferences = preferences()

        preferences.setDefaultRadiusMeters(1_000.0)

        assertEquals(100.0, preferences.data.first().defaultRadiusMeters, 0.0)
    }

    @Test
    fun resetClearsEverything() = runTest {
        val preferences = preferences()
        preferences.setCompletedOnboarding(true)
        preferences.setDefaultRadiusMeters(250.0)

        preferences.reset()

        assertEquals(UserPreferences(), preferences.data.first())
    }

    private fun TestScope.preferences(): AppPreferences {
        val store = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
        ) {
            File(folder.root, "settings.preferences_pb")
        }
        return AppPreferences(store)
    }
}
