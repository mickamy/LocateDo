package com.locatedo.locatedo.core.auth

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

// Android has no Apple SDK, so sign-in goes through Apple's web flow for the Services ID in a browser tab. Apple posts
// the result to the API server, which hands it back to the app's custom scheme with the values in the fragment.
object AppleSignIn {
    sealed interface Result {
        data class Success(val identityToken: String, val authorizationCode: String, val displayName: String?) : Result

        data object Canceled : Result

        data object Failed : Result
    }

    // The name scope makes Apple answer with a form post, which is why the redirect goes through the server.
    fun authorizeUrl(servicesId: String, redirectUri: String, state: String, nonce: String): String {
        val query = listOf(
            "client_id" to servicesId,
            "redirect_uri" to redirectUri,
            "response_type" to "code id_token",
            "response_mode" to "form_post",
            "scope" to "name",
            "state" to state,
            "nonce" to sha256(nonce),
        ).joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return "https://appleid.apple.com/auth/authorize?$query"
    }

    // A callback whose state is not the one this app sent is someone else's, and is refused.
    fun result(fragment: String?, expectedState: String): Result {
        val values = parse(fragment.orEmpty())
        if (values["state"] != expectedState) {
            return Result.Failed
        }
        if (values["error"] == "user_cancelled_authorize") {
            return Result.Canceled
        }
        val identityToken = values["id_token"]
        val authorizationCode = values["code"]
        if (identityToken.isNullOrEmpty() || authorizationCode.isNullOrEmpty()) {
            return Result.Failed
        }
        return Result.Success(identityToken, authorizationCode, displayName(values["given_name"], values["family_name"]))
    }

    // Like iOS's PersonNameComponents: names written in CJK scripts are family name first with no space.
    fun displayName(givenName: String?, familyName: String?): String? {
        val given = givenName?.trim().orEmpty()
        val family = familyName?.trim().orEmpty()
        if (given.isEmpty() && family.isEmpty()) {
            return null
        }
        if (given.isEmpty()) {
            return family
        }
        if (family.isEmpty()) {
            return given
        }
        if (isCjk(given + family)) {
            return family + given
        }
        return "$given $family"
    }

    private fun isCjk(text: String): Boolean = text.codePoints().anyMatch { codePoint ->
        when (Character.UnicodeScript.of(codePoint)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            -> true
            else -> false
        }
    }

    private fun parse(fragment: String): Map<String, String> = fragment
        .split("&")
        .filter { it.contains("=") }
        .associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            URLDecoder.decode(key, UTF_8) to URLDecoder.decode(value, UTF_8)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    // The Charset overloads of URLEncoder and URLDecoder need API 33.
    private const val UTF_8 = "UTF-8"
}
