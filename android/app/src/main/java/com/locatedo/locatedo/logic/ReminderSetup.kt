package com.locatedo.locatedo.logic

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import java.time.Duration
import java.time.Instant

// In the order the sheet lists them and the analytics name them.
enum class ReminderSetupNeed(val key: String) {
    NOTIFICATIONS("notifications"),
    LOCATION_ALWAYS("location_always"),
    PRECISE_LOCATION("precise_location"),
}

data class ReminderSetupRequest(val missing: List<ReminderSetupNeed>, val shownCount: Int, val placeName: String)

// What arrival reminders still need, and whether to ask for it after a place is saved: at most once a week, until the
// user says not to.
object ReminderSetup {
    val INTERVAL: Duration = Duration.ofDays(7)

    fun needsNotifications(permissions: Permissions): Boolean = permissions.notifications != NotificationAuth.AUTHORIZED

    fun needsAlways(permissions: Permissions): Boolean = permissions.location != LocationAuth.ALWAYS

    fun missing(permissions: Permissions): List<ReminderSetupNeed> = ReminderSetupNeed.entries.filter { need ->
        when (need) {
            ReminderSetupNeed.NOTIFICATIONS -> needsNotifications(permissions)
            ReminderSetupNeed.LOCATION_ALWAYS -> needsAlways(permissions)
            ReminderSetupNeed.PRECISE_LOCATION -> permissions.needsPreciseLocation
        }
    }

    fun analyticsValue(needs: List<ReminderSetupNeed>): String = needs.joinToString(",") { it.key }

    // Location turned off altogether is left to the home banner; this sheet asks for the steps up from "while in use".
    fun isDue(permissions: Permissions, shownAt: Instant?, never: Boolean, now: Instant): Boolean {
        if (never) {
            return false
        }
        val canStepUp = permissions.location == LocationAuth.WHEN_IN_USE || permissions.needsPreciseLocation
        if (!needsNotifications(permissions) && !canStepUp) {
            return false
        }
        if (shownAt == null) {
            return true
        }
        return Duration.between(shownAt, now) >= INTERVAL
    }
}
