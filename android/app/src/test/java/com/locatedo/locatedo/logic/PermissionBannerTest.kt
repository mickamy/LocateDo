package com.locatedo.locatedo.logic

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionBannerTest {
    @Test
    fun nothingWhenEverythingIsGranted() {
        assertNull(PermissionBanner.of(Permissions(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED), hasPlaces = true))
    }

    @Test
    fun whenInUseAsksForAllTheTimeOnceThereIsAPlace() {
        assertEquals(
            PermissionBanner.LOCATION_ALWAYS,
            PermissionBanner.of(Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED), hasPlaces = true),
        )
        assertNull(PermissionBanner.of(Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED), hasPlaces = false))
    }

    @Test
    fun deniedLocationShowsWithOrWithoutPlaces() {
        assertEquals(
            PermissionBanner.LOCATION_DENIED,
            PermissionBanner.of(Permissions(LocationAuth.DENIED, NotificationAuth.DENIED), hasPlaces = false),
        )
    }

    @Test
    fun locationComesBeforeNotifications() {
        assertEquals(
            PermissionBanner.LOCATION_ALWAYS,
            PermissionBanner.of(Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED), hasPlaces = true),
        )
        assertEquals(
            PermissionBanner.NOTIFICATIONS,
            PermissionBanner.of(Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED), hasPlaces = false),
        )
        assertEquals(
            PermissionBanner.NOTIFICATIONS,
            PermissionBanner.of(Permissions(LocationAuth.ALWAYS, NotificationAuth.DENIED), hasPlaces = true),
        )
    }

    @Test
    fun approximateLocationComesAfterLocationAndBeforeNotifications() {
        val approximate = Permissions(LocationAuth.ALWAYS, NotificationAuth.DENIED, preciseLocation = false)
        assertEquals(PermissionBanner.PRECISE_LOCATION, PermissionBanner.of(approximate, hasPlaces = true))
        assertEquals(
            PermissionBanner.LOCATION_ALWAYS,
            PermissionBanner.of(approximate.copy(location = LocationAuth.WHEN_IN_USE), hasPlaces = true),
        )
        assertNull(PermissionBanner.of(Permissions(LocationAuth.NOT_DETERMINED, NotificationAuth.AUTHORIZED, preciseLocation = false), hasPlaces = true))
    }

    @Test
    fun notYetAskedIsNotABanner() {
        assertNull(PermissionBanner.of(Permissions(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED), hasPlaces = true))
    }

    @Test
    fun notificationsNotYetAskedShowOnceLocationIsAllTheTime() {
        assertEquals(
            PermissionBanner.NOTIFICATIONS,
            PermissionBanner.of(Permissions(LocationAuth.ALWAYS, NotificationAuth.NOT_DETERMINED), hasPlaces = true),
        )
        assertNull(PermissionBanner.of(Permissions(LocationAuth.WHEN_IN_USE, NotificationAuth.NOT_DETERMINED), hasPlaces = false))
    }

    @Test
    fun keysMatchIos() {
        assertEquals(
            listOf("location_always", "location_denied", "precise_location", "notifications"),
            PermissionBanner.entries.map { it.key },
        )
    }
}
