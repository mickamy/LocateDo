package com.locatedo.locatedo.core.geofence

import java.time.Duration
import java.time.Instant

enum class GeofenceTransition {
    ENTER,
    DWELL,
    EXIT,
}

// What a geofence report means for a place: when the device went inside, and what to remind of. A departure needs a
// stay of five minutes, so walking past is not one. Play services reports no exit right after a fence is registered,
// only ENTER and DWELL for a device already inside, so a fresh registration never counts as leaving.
object PlacePresence {
    val MIN_STAY: Duration = Duration.ofMinutes(5)

    sealed interface Event {
        data object Arrival : Event

        data class Departure(val stay: Duration) : Event

        data class ShortStay(val stay: Duration) : Event
    }

    data class Outcome(val enteredAt: Instant?, val event: Event?)

    fun next(transition: GeofenceTransition, enteredAt: Instant?, now: Instant): Outcome = when (transition) {
        GeofenceTransition.ENTER -> Outcome(enteredAt ?: now, null)
        GeofenceTransition.DWELL -> Outcome(enteredAt ?: now, Event.Arrival)
        GeofenceTransition.EXIT -> exit(enteredAt, now)
    }

    private fun exit(enteredAt: Instant?, now: Instant): Outcome {
        if (enteredAt == null) {
            return Outcome(null, null)
        }
        val stay = Duration.between(enteredAt, now)
        if (stay >= MIN_STAY) {
            return Outcome(null, Event.Departure(stay))
        }
        return Outcome(null, Event.ShortStay(stay))
    }
}
