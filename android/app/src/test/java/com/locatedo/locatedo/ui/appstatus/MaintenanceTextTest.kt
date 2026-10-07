package com.locatedo.locatedo.ui.appstatus

import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenanceTextTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val starts = Instant.parse("2026-12-01T15:00:00Z")
    private val ends = Instant.parse("2026-12-01T17:00:00Z")

    @Test
    fun theEndShowsOnlyTheTimeWhenTheWindowStaysWithinOneDay() {
        val sameDay = AppStatusDocument.Maintenance(startsAt = starts, endsAt = ends)
        val overnight = AppStatusDocument.Maintenance(startsAt = starts, endsAt = ends.plusSeconds(86_400))
        val timeOnly = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US).withZone(tokyo)
        val withDate = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.US).withZone(tokyo)

        assertEquals(timeOnly.format(ends), MaintenanceText.end(sameDay, Locale.US, tokyo))
        assertEquals(withDate.format(overnight.endsAt), MaintenanceText.end(overnight, Locale.US, tokyo))
        assertEquals(withDate.format(starts), MaintenanceText.start(sameDay, Locale.US, tokyo))
    }
}
