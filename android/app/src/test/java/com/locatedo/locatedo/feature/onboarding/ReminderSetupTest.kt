package com.locatedo.locatedo.feature.onboarding

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSetupTest {
    private val now = Instant.parse("2026-10-09T00:00:00Z")
    private val whenInUse = Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED)

    @Test
    fun missingNamesWhatIsNotYetAllowed() {
        assertEquals(
            listOf(ReminderSetupNeed.NOTIFICATIONS, ReminderSetupNeed.LOCATION_ALWAYS),
            ReminderSetup.missing(Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED)),
        )
        assertEquals(listOf(ReminderSetupNeed.NOTIFICATIONS), ReminderSetup.missing(Permissions(LocationAuth.ALWAYS, NotificationAuth.NOT_DETERMINED)))
        assertEquals(listOf(ReminderSetupNeed.LOCATION_ALWAYS), ReminderSetup.missing(whenInUse))
        assertEquals(listOf(ReminderSetupNeed.LOCATION_ALWAYS), ReminderSetup.missing(Permissions(LocationAuth.DENIED, NotificationAuth.AUTHORIZED)))
        assertEquals(emptyList<ReminderSetupNeed>(), ReminderSetup.missing(Permissions(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED)))
    }

    @Test
    fun approximateLocationIsMissingOnlyOnceLocationIsAllowed() {
        assertEquals(
            listOf(ReminderSetupNeed.LOCATION_ALWAYS, ReminderSetupNeed.PRECISE_LOCATION),
            ReminderSetup.missing(whenInUse.copy(preciseLocation = false)),
        )
        assertEquals(
            listOf(ReminderSetupNeed.LOCATION_ALWAYS),
            ReminderSetup.missing(Permissions(LocationAuth.DENIED, NotificationAuth.AUTHORIZED, preciseLocation = false)),
        )
    }

    @Test
    fun onlyApproximateLocationMissingIsDue() {
        val approximate = Permissions(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED, preciseLocation = false)

        assertTrue(ReminderSetup.isDue(approximate, shownAt = null, never = false, now = now))
    }

    @Test
    fun analyticsNameEveryMissingStepInOrder() {
        assertEquals("notifications,location_always,precise_location", ReminderSetup.analyticsValue(ReminderSetupNeed.entries))
        assertEquals("precise_location", ReminderSetup.analyticsValue(listOf(ReminderSetupNeed.PRECISE_LOCATION)))
    }

    @Test
    fun theFirstShowingIsDue() {
        assertTrue(ReminderSetup.isDue(whenInUse, shownAt = null, never = false, now = now))
    }

    @Test
    fun aShowingIsDueAgainAfterAWeek() {
        assertFalse(ReminderSetup.isDue(whenInUse, shownAt = now.minus(Duration.ofDays(7)).plusSeconds(1), never = false, now = now))
        assertTrue(ReminderSetup.isDue(whenInUse, shownAt = now.minus(Duration.ofDays(7)), never = false, now = now))
    }

    @Test
    fun dontShowAgainIsNeverDue() {
        assertFalse(ReminderSetup.isDue(whenInUse, shownAt = null, never = true, now = now))
    }

    @Test
    fun nothingMissingIsNotDue() {
        assertFalse(ReminderSetup.isDue(Permissions(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED), shownAt = null, never = false, now = now))
    }

    @Test
    fun locationOffAloneIsLeftToTheHomeBanner() {
        for (location in listOf(LocationAuth.DENIED, LocationAuth.NOT_DETERMINED)) {
            assertFalse(location.name, ReminderSetup.isDue(Permissions(location, NotificationAuth.AUTHORIZED), shownAt = null, never = false, now = now))
            assertTrue(location.name, ReminderSetup.isDue(Permissions(location, NotificationAuth.DENIED), shownAt = null, never = false, now = now))
        }
    }
}
