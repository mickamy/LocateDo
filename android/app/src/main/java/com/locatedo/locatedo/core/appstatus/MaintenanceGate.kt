package com.locatedo.locatedo.core.appstatus

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Closed while the server is under maintenance or this build is too old to talk to it; every API call checks it.
@Singleton
class MaintenanceGate @Inject constructor(private val clock: Clock) {
    private class Window(val start: Instant, val end: Instant)

    @Volatile
    private var window: Window? = null

    @Volatile
    private var requiresUpdate = false

    fun update(maintenance: AppStatusDocument.Maintenance?, requiresUpdate: Boolean = false) {
        window = maintenance?.takeIf { it.startsAt.isBefore(it.endsAt) }?.let { Window(it.startsAt, it.endsAt) }
        this.requiresUpdate = requiresUpdate
    }

    fun isClosed(now: Instant = clock.instant()): Boolean {
        if (requiresUpdate) {
            return true
        }
        val window = window ?: return false
        return !now.isBefore(window.start) && now.isBefore(window.end)
    }
}
