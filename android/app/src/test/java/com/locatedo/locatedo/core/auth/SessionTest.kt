package com.locatedo.locatedo.core.auth

import com.locatedo.locatedo.testing.sessionProto
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val userId = UUID.fromString("0199bd00-0000-7000-8000-000000000001")

    @Test
    fun isExpiringWithinTheLeeway() {
        val session = Session(userId, "access", now.plusSeconds(60), "refresh")

        assertTrue(session.isExpiring(now, Duration.ofSeconds(60)))
        assertTrue(session.isExpiring(now.plusSeconds(1), Duration.ofSeconds(60)))
        assertFalse(session.isExpiring(now, Duration.ofSeconds(59)))
    }

    @Test
    fun comesFromTheServerSession() {
        val proto = sessionProto(userId.toString(), "access", "refresh", now)

        assertEquals(Session(userId, "access", now, "refresh"), Session.fromProto(proto))
        assertNull(Session.fromProto(sessionProto("not-a-uuid", "access", "refresh", now)))
    }

    @Test
    fun noncesAreLongRandomHex() {
        val first = Nonce.make()
        val second = Nonce.make()

        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(first, second)
    }
}
