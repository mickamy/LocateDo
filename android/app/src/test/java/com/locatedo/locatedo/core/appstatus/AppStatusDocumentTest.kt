package com.locatedo.locatedo.core.appstatus

import com.locatedo.locatedo.testing.SettableClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStatusDocumentTest {
    @Test
    fun decodesEveryFieldAndIgnoresUnknownKeys() {
        val document = AppStatusDocument.decode(FULL_JSON)

        assertEquals("1.2.0", document.minimumVersion?.android)
        assertEquals(STARTS, document.maintenance?.startsAt)
        assertEquals(ENDS, document.maintenance?.endsAt)
        assertEquals("移行のため", document.maintenance?.message?.text("ja"))
        assertEquals("2026-12-terms", document.notice?.id)
        assertEquals("Terms updated", document.notice?.message?.text("fr"))
    }

    @Test
    fun nullOrMissingSectionsMeanNothing() {
        val document = AppStatusDocument.decode("""{"minimum_version":{"ios":"1.0.0"},"maintenance":null}""")

        assertNull(document.minimumVersion?.android)
        assertNull(document.maintenance)
        assertNull(document.notice)
    }

    @Test
    fun comparesVersionsNumerically() {
        assertTrue(AppVersion.isOlder("1.9.0", "1.10.0"))
        assertFalse(AppVersion.isOlder("1.10.0", "1.9.0"))
        assertFalse(AppVersion.isOlder("1.0", "1.0.0"))
        assertTrue(AppVersion.isOlder("1.0", "1.0.1"))
        assertFalse(AppVersion.isOlder("2.0", "1.99.99"))
    }

    @Test
    fun phaseFollowsTheWindow() {
        val maintenance = checkNotNull(AppStatusDocument.decode(FULL_JSON).maintenance)

        assertEquals(MaintenancePhase.Upcoming(maintenance), MaintenancePhase.of(maintenance, STARTS.minusSeconds(1)))
        assertEquals(MaintenancePhase.Active(maintenance), MaintenancePhase.of(maintenance, STARTS))
        assertEquals(MaintenancePhase.None, MaintenancePhase.of(maintenance, ENDS))
        assertEquals(MaintenancePhase.None, MaintenancePhase.of(null, STARTS))
    }

    @Test
    fun theGateClosesOnlyInsideTheWindow() {
        val gate = MaintenanceGate(SettableClock(STARTS))
        gate.update(AppStatusDocument.decode(FULL_JSON).maintenance)

        assertFalse(gate.isClosed(STARTS.minusSeconds(1)))
        assertTrue(gate.isClosed(STARTS))
        assertTrue(gate.isClosed())
        assertFalse(gate.isClosed(ENDS))
    }

    @Test
    fun theGateStaysClosedWhileAnUpdateIsRequired() {
        val gate = MaintenanceGate(SettableClock(ENDS))
        gate.update(null, requiresUpdate = true)

        assertTrue(gate.isClosed())

        gate.update(null, requiresUpdate = false)

        assertFalse(gate.isClosed())
    }

    companion object {
        val STARTS: Instant = Instant.parse("2026-12-01T15:00:00Z")
        val ENDS: Instant = Instant.parse("2026-12-01T17:00:00Z")

        const val FULL_JSON = """
            {
              "minimum_version": { "ios": "1.0.0", "android": "1.2.0" },
              "maintenance": {
                "starts_at": "2026-12-01T15:00:00Z",
                "ends_at": "2026-12-01T17:00:00Z",
                "message": { "ja": "移行のため", "en": "Moving servers" }
              },
              "notice": {
                "id": "2026-12-terms",
                "until": "2026-12-31T00:00:00Z",
                "message": { "ja": "規約を更新しました", "en": "Terms updated" }
              },
              "something_new": true
            }
        """
    }
}
