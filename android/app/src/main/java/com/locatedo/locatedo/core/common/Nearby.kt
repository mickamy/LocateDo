package com.locatedo.locatedo.core.common

import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.PlaceWithTodos

data class NearbyPlace(val entry: PlaceWithTodos, val distanceMeters: Double?)

object Nearby {
    // Closest first when the device's location is known; otherwise the stored order.
    fun sort(places: List<PlaceWithTodos>, from: Coordinate?): List<NearbyPlace> {
        val nearby = places.map { entry ->
            NearbyPlace(entry, from?.let { Geo.distanceMeters(it, Coordinate(entry.place.latitude, entry.place.longitude)) })
        }
        if (from == null) {
            return nearby
        }
        return nearby.sortedBy { it.distanceMeters ?: Double.POSITIVE_INFINITY }
    }
}
