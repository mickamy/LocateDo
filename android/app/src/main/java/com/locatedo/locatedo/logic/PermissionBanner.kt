package com.locatedo.locatedo.logic

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions

// What keeps arrival reminders from working, one at a time and location first, as on iOS. Asking for "all the time"
// waits for a place to remind about.
enum class PermissionBanner(val key: String) {
    LOCATION_ALWAYS("location_always"),
    LOCATION_DENIED("location_denied"),
    PRECISE_LOCATION("precise_location"),
    NOTIFICATIONS("notifications"),
    ;

    companion object {
        fun of(permissions: Permissions, hasPlaces: Boolean): PermissionBanner? {
            val location = permissions.location
            if (location == LocationAuth.WHEN_IN_USE && hasPlaces) {
                return LOCATION_ALWAYS
            }
            if (location == LocationAuth.DENIED) {
                return LOCATION_DENIED
            }
            if (permissions.needsPreciseLocation) {
                return PRECISE_LOCATION
            }
            // Not yet asked is left to the reminder setup, until "all the time" shows reminders are wanted.
            val notifications = permissions.notifications
            if (notifications == NotificationAuth.DENIED) {
                return NOTIFICATIONS
            }
            if (notifications == NotificationAuth.NOT_DETERMINED && location == LocationAuth.ALWAYS) {
                return NOTIFICATIONS
            }
            return null
        }
    }
}
