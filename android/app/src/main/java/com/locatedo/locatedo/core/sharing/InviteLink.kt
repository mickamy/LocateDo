package com.locatedo.locatedo.core.sharing

import java.net.URI

// Invites travel as https://locatedo.com/i/<token>; the token alone is accepted too, for people who copy just that.
object InviteLink {
    private val hosts = setOf("locatedo.com", "www.locatedo.com")
    private val tokenPattern = Regex("^[A-Za-z0-9_-]{16,}$")

    fun url(token: String): String = "https://locatedo.com/i/$token"

    fun token(text: String): String? {
        val trimmed = text.trim()
        val host = runCatching { URI(trimmed) }.getOrNull()?.host
        if (host == null) {
            return validToken(trimmed)
        }
        if (host !in hosts) {
            return null
        }
        val parts = URI(trimmed).path.split('/')
        if (parts.size != 3 || parts[1] != "i") {
            return null
        }
        return validToken(parts[2])
    }

    private fun validToken(candidate: String): String? = candidate.takeIf { tokenPattern.matches(it) }
}
