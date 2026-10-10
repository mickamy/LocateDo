package com.locatedo.locatedo.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.Instant
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
        preferences.setRequestedLocation(true)
        preferences.setRequestedNotifications(true)
        preferences.recordReminderSetupShown(Instant.parse("2026-10-09T00:00:00Z"))
        preferences.setReminderSetupNever()
        preferences.setDefaultRadiusMeters(250.0)

        assertEquals(
            UserPreferences(
                hasCompletedOnboarding = true,
                hasRequestedLocation = true,
                hasRequestedNotifications = true,
                reminderSetupShownAt = Instant.parse("2026-10-09T00:00:00Z"),
                reminderSetupShownCount = 1,
                reminderSetupNever = true,
                defaultRadiusMeters = 250.0,
            ),
            preferences.data.first(),
        )
    }

    @Test
    fun analyticsConsentIsKeptAcrossResetsAndStartsOverWithADebugOverride() = runTest {
        val preferences = preferences()
        assertEquals(AnalyticsConsentRecord(), preferences.analyticsConsent.first())

        preferences.setAnalyticsConsent(false)
        preferences.setAnalyticsConsentRequired(true)
        preferences.reset()
        assertEquals(AnalyticsConsentRecord(answer = false, required = true), preferences.analyticsConsent.first())

        preferences.setConsentStoreCountryOverride("GB")
        assertEquals(AnalyticsConsentRecord(storeCountryOverride = "GB"), preferences.analyticsConsent.first())
    }

    @Test
    fun anOutOfRangeRadiusFallsBackToTheDefault() = runTest {
        val preferences = preferences()

        preferences.setDefaultRadiusMeters(1_000.0)

        assertEquals(100.0, preferences.data.first().defaultRadiusMeters, 0.0)
    }

    @Test
    fun theGeofenceRecordIsKeptApartFromThePreferences() = runTest {
        val preferences = preferences()

        preferences.setRegisteredGeofences("a|1.0|2.0|100.0")
        preferences.reset()

        assertEquals("a|1.0|2.0|100.0", preferences.registeredGeofences.first())
        assertEquals(UserPreferences(), preferences.data.first())
    }

    @Test
    fun resetClearsEverything() = runTest {
        val preferences = preferences()
        preferences.setCompletedOnboarding(true)
        preferences.setRequestedLocation(true)
        preferences.setRequestedNotifications(true)
        preferences.recordReminderSetupShown(Instant.parse("2026-10-09T00:00:00Z"))
        preferences.setReminderSetupNever()
        preferences.setDefaultRadiusMeters(250.0)

        preferences.reset()

        assertEquals(UserPreferences(), preferences.data.first())
    }

    @Test
    fun theFirstLaunchIsKeptAndTheAnalyticsRecordSurvivesAReset() = runTest {
        val preferences = preferences()
        val first = Instant.parse("2026-10-06T00:00:00Z")

        preferences.recordFirstLaunch(first)
        preferences.recordFirstLaunch(first.plusSeconds(86_400))
        preferences.setDailyStateReportedOn("2026-10-07")
        preferences.setLastReportedLocationAuth("always")
        preferences.reset()

        assertEquals(
            AnalyticsRecord(firstLaunchedAt = first, dailyStateReportedOn = "2026-10-07", lastReportedLocationAuth = "always"),
            preferences.analytics.first(),
        )
    }

    @Test
    fun promotionsConsentSurvivesAReset() = runTest {
        val preferences = preferences()
        assertEquals(PromotionsRecord(), preferences.promotions.first())

        preferences.setPromotionsConsent(true)
        preferences.setReceivedArrivalNotification()
        preferences.setShownPromotionsPrompt()
        preferences.reset()

        assertEquals(
            PromotionsRecord(consent = true, hasReceivedArrivalNotification = true, hasShownPrompt = true),
            preferences.promotions.first(),
        )
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
