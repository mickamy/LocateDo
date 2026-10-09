package com.locatedo.locatedo.feature.onboarding

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import java.time.Duration
import java.time.Instant

enum class ReminderSetupMissing(val key: String) {
    NOTIFICATIONS("notifications"),
    LOCATION_ALWAYS("location_always"),
    BOTH("both"),
}

data class ReminderSetupRequest(val missing: ReminderSetupMissing, val shownCount: Int)

// What arrival reminders still need, and whether to ask for it after a place is saved: at most once a week, until the
// user says not to.
object ReminderSetup {
    val INTERVAL: Duration = Duration.ofDays(7)

    fun needsNotifications(permissions: Permissions): Boolean = permissions.notifications != NotificationAuth.AUTHORIZED

    fun needsAlways(permissions: Permissions): Boolean = permissions.location != LocationAuth.ALWAYS

    fun missing(permissions: Permissions): ReminderSetupMissing? {
        val notifications = needsNotifications(permissions)
        val always = needsAlways(permissions)
        return when {
            notifications && always -> ReminderSetupMissing.BOTH
            notifications -> ReminderSetupMissing.NOTIFICATIONS
            always -> ReminderSetupMissing.LOCATION_ALWAYS
            else -> null
        }
    }

    // Location turned off altogether is left to the home banner; this sheet asks for the step up to "Always".
    fun isDue(permissions: Permissions, shownAt: Instant?, never: Boolean, now: Instant): Boolean {
        if (never) {
            return false
        }
        if (!needsNotifications(permissions) && permissions.location != LocationAuth.WHEN_IN_USE) {
            return false
        }
        if (shownAt == null) {
            return true
        }
        return Duration.between(shownAt, now) >= INTERVAL
    }
}
