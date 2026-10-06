package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.BuiltinCategory
import com.locatedo.locatedo.core.model.Category
import com.locatedo.locatedo.core.model.FreeLimit
import com.locatedo.locatedo.core.permissions.LocationAuth
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")

    @Test
    fun parametersAreKeyedByTheirWireNames() {
        val values = mapOf(AnalyticsParameter.TRIGGER to "share", AnalyticsParameter.OPEN_TODOS to 3).wireValues()

        assertEquals(mapOf("trigger" to "share", "open_todos" to 3L), values)
    }

    @Test
    fun flagsGoOutAsZeroOrOne() {
        val values = mapOf(AnalyticsParameter.PLAN to true, AnalyticsParameter.SOURCE to false).wireValues()

        assertEquals(1L, values["plan"])
        assertEquals(0L, values["source"])
    }

    @Test
    fun categoriesReportTheBuiltinKeyOrCustom() {
        val shopping = Category(id = uuidV7(now), builtin = BuiltinCategory.SHOPPING, icon = "cart", color = "green", sortOrder = 0, updatedAt = now)
        val kids = Category(id = uuidV7(now), name = "Kids", icon = "figure", color = "orange", sortOrder = 4, updatedAt = now)

        assertEquals("shopping", analyticsCategory(shopping))
        assertEquals("custom", analyticsCategory(kids))
        assertEquals("none", analyticsCategory(null))
    }

    @Test
    fun limitsAreNamedByWhatTheyCount() {
        assertEquals("place", FreeLimit.PLACES.analyticsKind)
        assertEquals("todo", FreeLimit.OPEN_TODOS.analyticsKind)
    }
}

class InstallDateTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun daysCountCalendarDaysNotElapsedHours() {
        val lateEvening = ZonedDateTime.of(2026, 10, 6, 23, 0, 0, 0, tokyo).toInstant()
        val nextMorning = ZonedDateTime.of(2026, 10, 7, 7, 0, 0, 0, tokyo).toInstant()

        assertEquals(0, InstallDate.daysSinceInstall(lateEvening, lateEvening, tokyo))
        assertEquals(1, InstallDate.daysSinceInstall(lateEvening, nextMorning, tokyo))
    }

    @Test
    fun anUnrecordedInstallCountsAsDayZero() {
        assertEquals(0, InstallDate.daysSinceInstall(null, Instant.parse("2026-10-06T00:00:00Z"), tokyo))
    }

    @Test
    fun aClockSetBackDoesNotGoNegative() {
        val installedAt = Instant.parse("2027-01-15T00:00:00Z")

        assertEquals(0, InstallDate.daysSinceInstall(installedAt, installedAt.minusSeconds(3 * 86_400), tokyo))
    }
}

class DailyStateScheduleTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun reportsOncePerCalendarDay() {
        val morning = ZonedDateTime.of(2026, 10, 6, 8, 0, 0, 0, tokyo).toInstant()
        val evening = ZonedDateTime.of(2026, 10, 6, 22, 0, 0, 0, tokyo).toInstant()
        val justAfterMidnight = ZonedDateTime.of(2026, 10, 7, 0, 5, 0, 0, tokyo).toInstant()

        assertTrue(DailyStateSchedule.isDue(null, morning, tokyo))
        val reported = DailyStateSchedule.day(morning, tokyo)
        assertFalse(DailyStateSchedule.isDue(reported, evening, tokyo))
        assertTrue(DailyStateSchedule.isDue(reported, justAfterMidnight, tokyo))
    }
}

class LocationAuthHistoryTest {
    @Test
    fun theFirstReadingIsNotAChange() {
        assertNull(LocationAuthHistory.change(null, LocationAuth.ALWAYS))
        assertNull(LocationAuthHistory.change(LocationAuth.ALWAYS.analyticsKey, LocationAuth.ALWAYS))
    }

    @Test
    fun aDowngradeIsReported() {
        val change = LocationAuthHistory.change(LocationAuth.ALWAYS.analyticsKey, LocationAuth.WHEN_IN_USE)

        assertEquals(LocationAuth.ALWAYS to LocationAuth.WHEN_IN_USE, change)
    }

    @Test
    fun anUnknownLastReadingIsNotAChange() {
        assertNull(LocationAuthHistory.change("sometimes", LocationAuth.ALWAYS))
    }
}
