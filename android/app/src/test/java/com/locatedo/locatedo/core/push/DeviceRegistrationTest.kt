package com.locatedo.locatedo.core.push

import com.connectrpc.Code
import com.locatedo.device.v1.Platform
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.Session
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.FakeDeviceService
import com.locatedo.locatedo.testing.FakePushTokenSource
import com.locatedo.locatedo.testing.InMemorySessionStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.util.Log, which the failure path writes to.
@RunWith(RobolectricTestRunner::class)
class DeviceRegistrationTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val devices = FakeDeviceService()
    private val source = FakePushTokenSource("fcm-token")
    private val session = Session(UUID.fromString(FakeAccountService.USER_ID), "access", now.plusSeconds(3600), "refresh")

    @Test
    fun registersTheTokenOnceSignedIn() = runTest {
        val registration = registration(InMemorySessionStore(session))

        registration.registerIfSignedIn()

        val request = devices.registered.single()
        assertEquals(Platform.PLATFORM_ANDROID, request.platform)
        assertEquals("fcm-token", request.pushToken)
        assertEquals("fcm-token", registration.pushToken.value)
    }

    @Test
    fun staysQuietWhileSignedOut() = runTest {
        val registration = registration(InMemorySessionStore())

        registration.registerIfSignedIn()
        registration.received("fresh-token")

        assertTrue(devices.registered.isEmpty())
        assertEquals("fresh-token", registration.pushToken.value)
    }

    @Test
    fun aNewTokenReplacesTheOldOne() = runTest {
        val registration = registration(InMemorySessionStore(session))
        registration.registerIfSignedIn()

        registration.received("fresh-token")

        assertEquals(listOf("fcm-token", "fresh-token"), devices.registered.map { it.pushToken })
    }

    @Test
    fun aFailedRegistrationIsNotAnError() = runTest {
        devices.failure = Code.UNAVAILABLE
        val registration = registration(InMemorySessionStore(session))

        registration.registerIfSignedIn()

        assertEquals(1, devices.registered.size)
    }

    @Test
    fun withoutATokenNothingIsSent() = runTest {
        source.token = null
        val registration = registration(InMemorySessionStore(session))

        registration.registerIfSignedIn()

        assertTrue(devices.registered.isEmpty())
    }

    private fun TestScope.registration(store: InMemorySessionStore): DeviceRegistration {
        val authenticator = Authenticator(store, FakeAccountService(), AccessTokenStore(), Clock.fixed(now, ZoneOffset.UTC), this)
        return DeviceRegistration(devices, authenticator, source)
    }
}
