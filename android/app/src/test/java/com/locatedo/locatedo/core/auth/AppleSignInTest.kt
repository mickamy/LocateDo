package com.locatedo.locatedo.core.auth

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleSignInTest {
    private val state = "state-0123456789abcdef"

    @Test
    fun theAuthorizeUrlAsksForTheNameAndSendsOnlyTheNonceHash() {
        val url = URI(
            AppleSignIn.authorizeUrl(
                servicesId = "com.locatedo.LocateDo.web",
                redirectUri = "https://api.locatedo.com/auth/apple/android",
                state = state,
                nonce = "abc",
            ),
        )
        val query = url.rawQuery.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }

        assertEquals("appleid.apple.com", url.host)
        assertEquals("/auth/authorize", url.path)
        assertEquals("com.locatedo.LocateDo.web", query["client_id"])
        assertEquals("https%3A%2F%2Fapi.locatedo.com%2Fauth%2Fapple%2Fandroid", query["redirect_uri"])
        assertEquals("code%20id_token", query["response_type"])
        assertEquals("form_post", query["response_mode"])
        assertEquals("name", query["scope"])
        assertEquals(state, query["state"])
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", query["nonce"])
    }

    @Test
    fun aCallbackCarriesTheTokensAndTheName() {
        val fragment = "code=c-1&id_token=eyJ.token.sig&state=$state&given_name=Taro&family_name=Yamada"

        assertEquals(
            AppleSignIn.Result.Success("eyJ.token.sig", "c-1", "Taro Yamada"),
            AppleSignIn.result(fragment, state),
        )
    }

    @Test
    fun theNameIsOnlySentTheFirstTime() {
        val result = AppleSignIn.result("code=c-1&id_token=t&state=$state", state)

        assertEquals(AppleSignIn.Result.Success("t", "c-1", null), result)
    }

    @Test
    fun encodedValuesAreDecoded() {
        val result = AppleSignIn.result("code=c-1&id_token=t&state=$state&given_name=%E5%A4%AA%E9%83%8E&family_name=%E5%B1%B1%E7%94%B0", state)

        assertEquals(AppleSignIn.Result.Success("t", "c-1", "山田太郎"), result)
    }

    @Test
    fun anotherStateIsRefused() {
        assertEquals(AppleSignIn.Result.Failed, AppleSignIn.result("code=c-1&id_token=t&state=other", state))
        assertEquals(AppleSignIn.Result.Failed, AppleSignIn.result(null, state))
    }

    @Test
    fun cancelingIsNotAFailure() {
        assertEquals(AppleSignIn.Result.Canceled, AppleSignIn.result("error=user_cancelled_authorize&state=$state", state))
    }

    @Test
    fun aCallbackWithoutTokensFails() {
        assertEquals(AppleSignIn.Result.Failed, AppleSignIn.result("error=invalid_request&state=$state", state))
        assertEquals(AppleSignIn.Result.Failed, AppleSignIn.result("code=c-1&state=$state", state))
    }

    @Test
    fun namesAreJoinedByScript() {
        assertEquals("Taro Yamada", AppleSignIn.displayName("Taro", "Yamada"))
        assertEquals("山田太郎", AppleSignIn.displayName("太郎", "山田"))
        assertEquals("Taro", AppleSignIn.displayName(" Taro ", ""))
        assertEquals("山田", AppleSignIn.displayName(null, "山田"))
        assertNull(AppleSignIn.displayName(" ", null))
    }
}
