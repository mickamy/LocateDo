package com.locatedo.locatedo.core.common

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidV7Test {
    @Test
    fun setsVersionAndVariantBits() {
        val id = uuidV7()
        assertEquals(7, id.version())
        assertEquals(2, id.variant())
    }

    @Test
    fun encodesMillisecondTimestamp() {
        val id = uuidV7(Instant.ofEpochMilli(1_700_000_000_123))
        assertEquals(1_700_000_000_123, id.mostSignificantBits ushr 16)
    }

    @Test
    fun readsBackTheTimestamp() {
        val now = Instant.ofEpochMilli(1_700_000_000_123)
        assertEquals(now, uuidV7(now).v7Instant())
    }

    @Test
    fun otherVersionsHaveNoTimestamp() {
        assertNull(UUID.fromString("0199bd00-0000-4000-8000-000000000001").v7Instant())
    }

    @Test
    fun sortsByTime() {
        val earlier = uuidV7(Instant.ofEpochSecond(1_000))
        val later = uuidV7(Instant.ofEpochSecond(1_001))
        assertTrue(earlier.toString() < later.toString())
    }

    @Test
    fun isUniqueWithinTheSameMillisecond() {
        val now = Instant.now()
        val ids = (1..1_000).map { uuidV7(now) }.toSet()
        assertEquals(1_000, ids.size)
    }
}
