package com.locatedo.locatedo.core.geofence

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacePresenceTest {
    private val now = Instant.parse("2026-10-10T09:00:00Z")

    @Test
    fun goingInStartsTheClockWithoutOverwritingIt() {
        val earlier = now.minus(Duration.ofMinutes(3))

        assertEquals(PlacePresence.Outcome(now, null), PlacePresence.next(GeofenceTransition.ENTER, null, now))
        assertEquals(PlacePresence.Outcome(earlier, null), PlacePresence.next(GeofenceTransition.ENTER, earlier, now))
    }

    @Test
    fun aDwellIsAnArrival() {
        assertEquals(
            PlacePresence.Outcome(now, PlacePresence.Event.Arrival),
            PlacePresence.next(GeofenceTransition.DWELL, null, now),
        )
    }

    @Test
    fun leavingAfterFiveMinutesIsADeparture() {
        val enteredAt = now.minus(PlacePresence.MIN_STAY)

        assertEquals(
            PlacePresence.Outcome(null, PlacePresence.Event.Departure(PlacePresence.MIN_STAY)),
            PlacePresence.next(GeofenceTransition.EXIT, enteredAt, now),
        )
    }

    @Test
    fun leavingSoonerIsAShortStay() {
        val stay = Duration.ofMinutes(4)

        assertEquals(
            PlacePresence.Outcome(null, PlacePresence.Event.ShortStay(stay)),
            PlacePresence.next(GeofenceTransition.EXIT, now.minus(stay), now),
        )
    }

    @Test
    fun leavingWithoutHavingSeenTheDeviceGoInIsNothing() {
        assertEquals(PlacePresence.Outcome(null, null), PlacePresence.next(GeofenceTransition.EXIT, null, now))
    }
}
