package com.locatedo.locatedo.core.common

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

private val random = SecureRandom()

// Time-ordered IDs that the server accepts as they are; the order only holds on one device's clock.
fun uuidV7(now: Instant = Instant.now()): UUID {
    val millis = now.toEpochMilli()
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    for (index in 0 until 6) {
        bytes[index] = (millis shr (8 * (5 - index))).toByte()
    }
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val buffer = ByteBuffer.wrap(bytes)
    return UUID(buffer.long, buffer.long)
}

fun UUID.v7Instant(): Instant? {
    if (version() != 7) {
        return null
    }
    return Instant.ofEpochMilli(mostSignificantBits ushr 16)
}
