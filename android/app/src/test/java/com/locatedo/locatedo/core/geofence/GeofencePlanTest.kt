package com.locatedo.locatedo.core.geofence

import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofencePlanTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")

    @Test
    fun keepsTheNearestNinetyNinePlaces() {
        val places = (0 until 105).map { index -> place("Place $index", latitude = 35.0 + index * 0.01) }

        val regions = GeofencePlan.regions(places.shuffled(), near = Coordinate(35.0, 139.0))

        assertEquals(99, regions.size)
        assertEquals(places[0].id, regions.first().id)
        assertEquals(places[98].id, regions.last().id)
    }

    @Test
    fun keepsTheStoredOrderWithoutALocation() {
        val places = (0 until 3).map { index -> place("Place $index", latitude = 35.0 - index * 0.01) }

        val regions = GeofencePlan.regions(places, near = null)

        assertEquals(places.map { it.id }, regions.map { it.id })
    }

    @Test
    fun diffsAgainstTheCurrentRegistrations() {
        val unchanged = place("Unchanged", latitude = 35.0)
        val resized = place("Resized", latitude = 36.0)
        val added = place("Added", latitude = 37.0)
        val stale = place("Stale", latitude = 0.0)
        val current = listOf(unchanged, resized, stale).map(::GeofenceRegion).associateBy { it.requestId }

        val desired = GeofencePlan.regions(listOf(unchanged, resized.copy(radiusMeters = 200.0), added), near = null)
        val changes = GeofencePlan.changes(current, desired)

        assertEquals(listOf(resized.id, added.id), changes.add.map { it.id })
        assertEquals(listOf(stale.id.toString()), changes.remove)
    }

    @Test
    fun theRecordRoundTripsAndSkipsWhatItCannotRead() {
        val regions = listOf(place("Store", latitude = 35.5), place("Office", latitude = 35.6).copy(radiusMeters = 250.0)).map(::GeofenceRegion)

        val decoded = GeofenceRecord.decode(GeofenceRecord.encode(regions) + "\nnot-a-uuid|1|2|3\n|||\n")

        assertEquals(regions.associateBy { it.requestId }, decoded)
        assertTrue(GeofenceRecord.decode("").isEmpty())
        assertEquals("", GeofenceRecord.encode(emptyList()))
    }

    private fun place(name: String, latitude: Double) =
        Place(id = uuidV7(now), name = name, latitude = latitude, longitude = 139.0, createdAt = now)
}
