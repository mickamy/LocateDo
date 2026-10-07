package com.locatedo.locatedo.core.push

import com.connectrpc.Code
import com.locatedo.device.v1.Platform
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.Session
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.FakeDeviceService
import com.locatedo.locatedo.testing.FakeDisplayLanguage
import com.locatedo.locatedo.testing.FakeInstallationIdSource
import com.locatedo.locatedo.testing.InMemorySessionStore
import com.locatedo.locatedo.testing.testPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.util.Log, which the failure path writes to.
@RunWith(RobolectricTestRunner::class)
class DeviceRegistrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val devices = FakeDeviceService()
    private val source = FakeInstallationIdSource("installation-1")
    private val language = FakeDisplayLanguage("ja")
    private val session = Session(UUID.fromString(FakeAccountService.USER_ID), "access", now.plusSeconds(3600), "refresh")

    @Test
    fun registersTheInstallationOnceSignedIn() = runTest {
        val (registration, _) = registration(InMemorySessionStore(session))

        registration.registerIfNeeded()

        val request = devices.registered.single()
        assertEquals(Platform.PLATFORM_ANDROID, request.platform)
        assertEquals("installation-1", request.pushToken)
        assertFalse(request.promotionsConsent)
        assertEquals("ja", request.language)
        assertEquals("installation-1", registration.installationId.value)
    }

    @Test
    fun signedInDevicesRegisterWhateverTheConsent() = runTest {
        val (registration, preferences) = registration(InMemorySessionStore(session))
        registration.registerIfNeeded()
        preferences.setPromotionsConsent(true)

        registration.registerIfNeeded()

        assertEquals(listOf(false, true), devices.registered.map { it.promotionsConsent })
    }

    @Test
    fun staysQuietWhileSignedOutWithoutConsent() = runTest {
        val (registration, _) = registration(InMemorySessionStore())

        registration.registerIfNeeded()
        registration.received("installation-2")

        assertTrue(devices.registered.isEmpty())
        assertEquals("installation-2", registration.installationId.value)
    }

    @Test
    fun registersAnonymouslyWhileConsentIsOn() = runTest {
        val (registration, preferences) = registration(InMemorySessionStore())
        preferences.setPromotionsConsent(true)

        registration.registerIfNeeded()

        assertTrue(devices.registered.single().promotionsConsent)
    }

    @Test
    fun turningConsentOffWhileSignedOutIsSentOnce() = runTest {
        val (registration, preferences) = registration(InMemorySessionStore())
        preferences.setPromotionsConsent(true)
        registration.registerIfNeeded()
        preferences.setPromotionsConsent(false)

        registration.consentChanged()
        registration.registerIfNeeded()

        assertEquals(listOf(true, false), devices.registered.map { it.promotionsConsent })
    }

    @Test
    fun aChangedIdReplacesTheOldOne() = runTest {
        val (registration, _) = registration(InMemorySessionStore(session))
        registration.registerIfNeeded()

        registration.received("installation-2")

        assertEquals(listOf("installation-1", "installation-2"), devices.registered.map { it.pushToken })
    }

    @Test
    fun aFailedRegistrationIsNotAnError() = runTest {
        devices.failure = Code.UNAVAILABLE
        val (registration, preferences) = registration(InMemorySessionStore())
        preferences.setPromotionsConsent(true)

        registration.registerIfNeeded()

        assertEquals(1, devices.registered.size)
    }

    @Test
    fun withoutAnIdNothingIsSent() = runTest {
        source.installationId = null
        val (registration, _) = registration(InMemorySessionStore(session))

        registration.registerIfNeeded()

        assertTrue(devices.registered.isEmpty())
    }

    private fun TestScope.registration(store: InMemorySessionStore): Pair<DeviceRegistration, AppPreferences> {
        val authenticator = Authenticator(store, FakeAccountService(), AccessTokenStore(), Clock.fixed(now, ZoneOffset.UTC), this)
        val preferences = testPreferences(folder.root, backgroundScope)
        return DeviceRegistration(devices, authenticator, source, preferences, language) to preferences
    }
}
