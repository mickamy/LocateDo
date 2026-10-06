package com.locatedo.locatedo.core.common

import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import com.locatedo.locatedo.core.model.PlaceWithTodos
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val shibuya = entry("Shibuya", 35.6580, 139.7016)
    private val shinjuku = entry("Shinjuku", 35.6896, 139.7006)
    private val yokohama = entry("Yokohama", 35.4437, 139.6380)

    @Test
    fun sortsByDistanceFromTheCurrentLocation() {
        val nearShinjuku = Coordinate(35.6900, 139.7000)

        val nearby = Nearby.sort(listOf(yokohama, shibuya, shinjuku), from = nearShinjuku)

        assertEquals(listOf("Shinjuku", "Shibuya", "Yokohama"), nearby.map { it.entry.place.name })
        assertTrue((nearby[0].distanceMeters ?: Double.MAX_VALUE) < 200)
    }

    @Test
    fun keepsTheStoredOrderWithoutALocation() {
        val nearby = Nearby.sort(listOf(shibuya, shinjuku, yokohama), from = null)

        assertEquals(listOf("Shibuya", "Shinjuku", "Yokohama"), nearby.map { it.entry.place.name })
        assertTrue(nearby.all { it.distanceMeters == null })
    }

    private fun entry(name: String, latitude: Double, longitude: Double) = PlaceWithTodos(
        place = Place(id = uuidV7(now), name = name, latitude = latitude, longitude = longitude, createdAt = now),
        todos = emptyList(),
    )
}
