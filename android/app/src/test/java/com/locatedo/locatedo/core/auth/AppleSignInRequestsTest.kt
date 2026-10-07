package com.locatedo.locatedo.core.auth

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleSignInRequestsTest {
    private val requests = AppleSignInRequests(
        AppleSignInConfig("com.locatedo.LocateDo.web", "https://api-stg.locatedo.com/auth/apple/android"),
    )

    @Test
    fun aBuildWithoutTheServicesIdOffersNothing() {
        val disabled = AppleSignInRequests(AppleSignInConfig("", ""))

        assertFalse(disabled.isAvailable)
        assertNull(disabled.start())
    }

    @Test
    fun theCallbackForTheStartedSignInCompletesIt() {
        val state = state(requests.start())

        requests.received("code=c-1&id_token=t&state=$state&given_name=Taro&family_name=Yamada")

        val completion = requests.completed.value
        assertNotNull(completion)
        assertEquals(AppleSignIn.Result.Success("t", "c-1", "Taro Yamada"), completion?.result)
        assertEquals(64, completion?.nonce?.length)
    }

    @Test
    fun aCallbackWithAnotherStateFails() {
        requests.start()

        requests.received("code=c-1&id_token=t&state=forged")

        assertEquals(AppleSignIn.Result.Failed, requests.completed.value?.result)
    }

    @Test
    fun aCallbackWithNoSignInUnderWayIsIgnored() {
        requests.received("code=c-1&id_token=t&state=any")

        assertNull(requests.completed.value)
    }

    @Test
    fun aCompletionIsTakenOnce() {
        val state = state(requests.start())
        requests.received("code=c-1&id_token=t&state=$state")
        val completion = requests.completed.value!!

        assertTrue(requests.consume(completion))
        assertFalse(requests.consume(completion))
        assertNull(requests.completed.value)
    }

    private fun state(url: String?): String = URI(url).rawQuery.split("&").first { it.startsWith("state=") }.substringAfter("=")
}
