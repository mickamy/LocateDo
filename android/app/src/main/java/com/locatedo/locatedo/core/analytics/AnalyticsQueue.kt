package com.locatedo.locatedo.core.analytics

// Holds what is logged before the person's answer is known, in memory only, so nothing leaves the device without it.
class AnalyticsQueue {
    sealed interface Entry {
        data class Event(val name: String, val parameters: Map<String, Any>) : Entry

        data class UserProperty(val name: String, val value: String?) : Entry
    }

    enum class Mode {
        HOLDING,
        SENDING,
        DROPPING,
    }

    private val held = mutableListOf<Entry>()

    var mode = Mode.HOLDING
        private set

    val entries: List<Entry>
        get() = held.toList()

    // Whether the entry goes out now.
    fun submit(entry: Entry): Boolean = when (mode) {
        Mode.SENDING -> true
        Mode.HOLDING -> {
            if (held.size < LIMIT) {
                held += entry
            }
            false
        }
        Mode.DROPPING -> false
    }

    // null keeps holding; true hands back what was held so it can be sent; false drops it.
    fun decide(decision: Boolean?): List<Entry> {
        if (decision == null) {
            mode = Mode.HOLDING
            return emptyList()
        }
        val released = held.toList()
        held.clear()
        if (decision) {
            mode = Mode.SENDING
            return released
        }
        mode = Mode.DROPPING
        return emptyList()
    }

    companion object {
        const val LIMIT = 100
    }
}
