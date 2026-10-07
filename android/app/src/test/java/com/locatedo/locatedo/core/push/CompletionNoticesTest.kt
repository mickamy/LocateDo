package com.locatedo.locatedo.core.push

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.auth.Session
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeDeviceService
import com.locatedo.locatedo.testing.FakeDisplayLanguage
import com.locatedo.locatedo.testing.FakeInstallationIdSource
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import com.locatedo.locatedo.testing.testSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log in the registration's failure path.
@RunWith(RobolectricTestRunner::class)
class CompletionNoticesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val devices = FakeDeviceService()
    private val analytics = FakeAnalytics()

    @Test
    fun isOnUntilTurnedOff() = runTest {
        val notices = notices(testSession)

        assertTrue(notices.isOn.first())
    }

    @Test
    fun aChangeIsReportedAndSentWithTheRegistration() = runTest {
        val notices = notices(testSession)

        notices.set(false)?.join()
        assertNull(notices.set(false))
        notices.set(true)?.join()

        assertEquals(listOf(false, true), devices.registered.map { it.completionNotices })
        assertEquals(2, analytics.count(AnalyticsEvent.COMPLETION_NOTICES_CHANGED))
        assertEquals("on", analytics.values(AnalyticsEvent.COMPLETION_NOTICES_CHANGED)["to"])
    }

    @Test
    fun aSignedOutDeviceKeepsTheChangeForLater() = runTest {
        val notices = notices(session = null)

        notices.set(false)?.join()

        assertTrue(devices.registered.isEmpty())
        assertEquals(false, notices.isOn.first())
    }

    private fun TestScope.notices(session: Session?): CompletionNotices {
        val preferences = testPreferences(folder.root, backgroundScope)
        val registration = DeviceRegistration(
            devices,
            fakeAuthenticator(session),
            FakeInstallationIdSource("installation-1"),
            preferences,
            FakeDisplayLanguage(),
        )
        return CompletionNotices(preferences, registration, analytics, this)
    }
}
