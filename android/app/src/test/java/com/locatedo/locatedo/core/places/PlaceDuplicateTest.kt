package com.locatedo.locatedo.core.places

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceDuplicateTest {
    private val now = Instant.parse("2026-10-10T00:00:00Z")
    private val store = place("Store", 35.6800, 139.7000)

    @Test
    fun aPickOnTheSameStoreFindsIt() {
        assertEquals("Store", PlaceDuplicate.nearest(Coordinate(35.68003, 139.70002), listOf(store))?.name)
    }

    @Test
    fun aPickAcrossTheStreetIsANewPlace() {
        assertNull(PlaceDuplicate.nearest(Coordinate(35.6806, 139.7000), listOf(store)))
    }

    @Test
    fun theClosestOfSeveralWins() {
        val next = place("Next door", 35.68025, 139.7000)

        assertEquals("Next door", PlaceDuplicate.nearest(Coordinate(35.68022, 139.7000), listOf(store, next))?.name)
    }

    private fun place(name: String, latitude: Double, longitude: Double) =
        Place(id = uuidV7(now), name = name, latitude = latitude, longitude = longitude, createdAt = now)
}
