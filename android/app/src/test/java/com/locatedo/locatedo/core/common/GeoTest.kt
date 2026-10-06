package com.locatedo.locatedo.core.common

import com.locatedo.locatedo.core.model.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    @Test
    fun measuresShibuyaToShinjuku() {
        val shibuya = Coordinate(35.6580, 139.7016)
        val shinjuku = Coordinate(35.6896, 139.7006)

        assertEquals(3_515.0, Geo.distanceMeters(shibuya, shinjuku), 30.0)
    }

    @Test
    fun theSamePointIsZero() {
        val point = Coordinate(35.0, 139.0)

        assertEquals(0.0, Geo.distanceMeters(point, point), 0.0)
    }
}
