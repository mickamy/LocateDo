package com.locatedo.locatedo.ui.appstatus

import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object MaintenanceText {
    fun start(maintenance: AppStatusDocument.Maintenance, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime(maintenance.startsAt, locale, zone)

    // The end shows only its time when the window stays within one day.
    fun end(maintenance: AppStatusDocument.Maintenance, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        val startsOn = maintenance.startsAt.atZone(zone).toLocalDate()
        val endsOn = maintenance.endsAt.atZone(zone).toLocalDate()
        if (startsOn == endsOn) {
            return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(maintenance.endsAt)
        }
        return dateTime(maintenance.endsAt, locale, zone)
    }

    private fun dateTime(instant: Instant, locale: Locale, zone: ZoneId): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)
}
