package com.locatedo.locatedo.core.analytics

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object InstallDate {
    // Calendar days, not elapsed hours: an install late one evening is one day old the next morning.
    fun daysSinceInstall(firstLaunchedAt: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int {
        if (firstLaunchedAt == null) {
            return 0
        }
        val installed = firstLaunchedAt.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(installed, today).coerceAtLeast(0).toInt()
    }
}
