package com.locatedo.locatedo.core.places

import com.locatedo.locatedo.core.model.Coordinate
import com.locatedo.locatedo.core.model.Place
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// A saved place close enough to a new pick to be the same one: the same store's label lands within a few meters, and
// a neighboring store is usually farther than this.
object PlaceDuplicate {
    const val DISTANCE_METERS = 50.0
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun nearest(coordinate: Coordinate, places: List<Place>): Place? {
        var nearest: Place? = null
        var nearestDistance = DISTANCE_METERS
        for (place in places) {
            val gap = distance(coordinate, Coordinate(place.latitude, place.longitude))
            if (gap <= nearestDistance) {
                nearest = place
                nearestDistance = gap
            }
        }
        return nearest
    }

    private fun distance(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }
}

enum class PlaceDuplicateChoice(val key: String) {
    OPEN("open"),
    ADD("add"),
    CANCEL("cancel"),
}
