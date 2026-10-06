package com.locatedo.locatedo.core.auth

import com.locatedo.locatedo.core.sync.toInstant
import java.time.Duration
import java.time.Instant
import java.util.UUID

class SignedOutException : Exception("signed out")

class InvalidSessionException : Exception("the server returned an unusable session")

data class Session(
    val userId: UUID,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
) {
    fun isExpiring(now: Instant, leeway: Duration): Boolean = Duration.between(now, accessTokenExpiresAt) <= leeway

    companion object {
        fun fromProto(proto: com.locatedo.account.v1.Session): Session? {
            val userId = runCatching { UUID.fromString(proto.userId) }.getOrNull() ?: return null
            return Session(
                userId = userId,
                accessToken = proto.accessToken,
                accessTokenExpiresAt = proto.accessTokenExpiresAt.toInstant(),
                refreshToken = proto.refreshToken,
            )
        }
    }
}
