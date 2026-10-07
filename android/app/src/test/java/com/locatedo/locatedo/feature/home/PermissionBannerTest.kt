package com.locatedo.locatedo.feature.home

import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PermissionBannerTest {
    @Test
    fun nothingWhenEverythingIsGranted() {
        assertNull(PermissionBanner.of(LocationAuth.ALWAYS, NotificationAuth.AUTHORIZED, hasPlaces = true))
    }

    @Test
    fun whenInUseAsksForAllTheTimeOnceThereIsAPlace() {
        assertEquals(
            PermissionBanner.LOCATION_ALWAYS,
            PermissionBanner.of(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED, hasPlaces = true),
        )
        assertNull(PermissionBanner.of(LocationAuth.WHEN_IN_USE, NotificationAuth.AUTHORIZED, hasPlaces = false))
    }

    @Test
    fun deniedLocationShowsWithOrWithoutPlaces() {
        assertEquals(
            PermissionBanner.LOCATION_DENIED,
            PermissionBanner.of(LocationAuth.DENIED, NotificationAuth.DENIED, hasPlaces = false),
        )
    }

    @Test
    fun locationComesBeforeNotifications() {
        assertEquals(
            PermissionBanner.LOCATION_ALWAYS,
            PermissionBanner.of(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED, hasPlaces = true),
        )
        assertEquals(
            PermissionBanner.NOTIFICATIONS,
            PermissionBanner.of(LocationAuth.WHEN_IN_USE, NotificationAuth.DENIED, hasPlaces = false),
        )
        assertEquals(
            PermissionBanner.NOTIFICATIONS,
            PermissionBanner.of(LocationAuth.ALWAYS, NotificationAuth.DENIED, hasPlaces = true),
        )
    }

    @Test
    fun notYetAskedIsNotABanner() {
        assertNull(PermissionBanner.of(LocationAuth.NOT_DETERMINED, NotificationAuth.NOT_DETERMINED, hasPlaces = true))
    }

    @Test
    fun keysMatchIos() {
        assertEquals(
            listOf("location_always", "location_denied", "notifications"),
            PermissionBanner.entries.map { it.key },
        )
    }
}
