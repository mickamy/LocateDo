package com.locatedo.locatedo.core.geofence

import com.locatedo.locatedo.core.common.Geo
import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import java.util.UUID

data class GeofenceRegion(
    val id: UUID,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
) {
    constructor(place: Place) : this(place.id, place.latitude, place.longitude, place.radiusMeters)

    val requestId: String
        get() = id.toString()
}

data class GeofenceChanges(val add: List<GeofenceRegion>, val remove: List<String>)

// The nearest places get a fence; the plan is recomputed whenever the places or the location change.
object GeofencePlan {
    const val LIMIT = 99

    fun regions(places: List<Place>, near: Coordinate?): List<GeofenceRegion> {
        val ordered = if (near == null) {
            places
        } else {
            places.sortedBy { Geo.distanceMeters(near, Coordinate(it.latitude, it.longitude)) }
        }
        return ordered.take(LIMIT).map(::GeofenceRegion)
    }

    fun changes(current: Map<String, GeofenceRegion>, desired: List<GeofenceRegion>): GeofenceChanges {
        val add = desired.filter { current[it.requestId] != it }
        val desiredIds = desired.map { it.requestId }.toSet()
        val remove = current.keys.filter { it !in desiredIds }.sorted()
        return GeofenceChanges(add, remove)
    }
}

// The geofencing client cannot list what it holds, so the app keeps its own copy, one region per line.
object GeofenceRecord {
    fun encode(regions: Collection<GeofenceRegion>): String =
        regions.joinToString("\n") { "${it.id}|${it.latitude}|${it.longitude}|${it.radiusMeters}" }

    fun decode(encoded: String): Map<String, GeofenceRegion> =
        encoded.lineSequence().mapNotNull(::decodeLine).associateBy { it.requestId }

    private fun decodeLine(line: String): GeofenceRegion? {
        val parts = line.split('|')
        if (parts.size != 4) {
            return null
        }
        val id = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
        val latitude = parts[1].toDoubleOrNull() ?: return null
        val longitude = parts[2].toDoubleOrNull() ?: return null
        val radius = parts[3].toDoubleOrNull() ?: return null
        return GeofenceRegion(id, latitude, longitude, radius)
    }
}
