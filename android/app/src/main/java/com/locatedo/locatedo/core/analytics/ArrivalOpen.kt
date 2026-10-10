package com.locatedo.locatedo.core.analytics

import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class TodoAddVia(val key: String) {
    TODO_EDITOR("todo_editor"),
    PLACE_EDITOR("place_editor"),
}

enum class CompletionVia(val key: String) {
    NOTIFICATION("notification"),
    APP("app"),
    ACTION("action"),
    ;

    val isFromReminder: Boolean
        get() = this == NOTIFICATION || this == ACTION
}

// The place the to-do editor started with: the nearest one from Home's plus menu, the place it was opened on, the
// first place when neither gave one, or none when there were no places.
enum class PlacePreset(val key: String) {
    NEAREST("nearest"),
    PLACE("place"),
    FIRST("first"),
    NONE("none"),
}

data class TodoAddOrigin(val entry: ScreenEntry, val placePreset: PlacePreset, val placeChanged: Boolean)

// A to-do checked off at the notified place soon after opening the arrival notification counts as done through it.
data class ArrivalOpen(val placeId: UUID, val openedAt: Instant) {
    fun via(completingAt: UUID?, now: Instant): CompletionVia {
        if (completingAt != placeId) {
            return CompletionVia.APP
        }
        val elapsed = Duration.between(openedAt, now)
        if (!elapsed.isNegative && elapsed <= WINDOW) {
            return CompletionVia.NOTIFICATION
        }
        return CompletionVia.APP
    }

    companion object {
        val WINDOW: Duration = Duration.ofMinutes(30)
    }
}
