package com.locatedo.locatedo.core.push

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsUserProperty
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeDeviceService
import com.locatedo.locatedo.testing.FakeDisplayLanguage
import com.locatedo.locatedo.testing.FakeInstallationIdSource
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testPreferences
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log in the registration's failure path.
@RunWith(RobolectricTestRunner::class)
class PromotionsConsentTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val devices = FakeDeviceService()
    private val analytics = FakeAnalytics()

    @Test
    fun doesNotAskBeforeTheFirstArrival() = runTest {
        val (consent, _) = consent()

        assertFalse(consent.shouldPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = fixedNow))
    }

    @Test
    fun asksAfterTheFirstArrival() = runTest {
        val (consent, _) = consent()
        consent.arrivalNotified()

        assertTrue(consent.shouldPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = fixedNow))
    }

    @Test
    fun doesNotAskWithoutNotificationPermission() = runTest {
        val (consent, _) = consent()
        consent.arrivalNotified()

        assertFalse(consent.shouldPrompt(NotificationAuth.DENIED, lastArrivalOpenedAt = null, now = fixedNow))
        assertFalse(consent.shouldPrompt(NotificationAuth.NOT_DETERMINED, lastArrivalOpenedAt = null, now = fixedNow))
    }

    @Test
    fun waitsAfterAnArrivalNotificationWasOpened() = runTest {
        val (consent, _) = consent()
        consent.arrivalNotified()
        val quiet = PromotionsConsent.QUIET_PERIOD_AFTER_ARRIVAL_OPENED

        assertFalse(consent.shouldPrompt(NotificationAuth.AUTHORIZED, fixedNow.minusSeconds(60), fixedNow))
        assertTrue(consent.shouldPrompt(NotificationAuth.AUTHORIZED, fixedNow.minus(quiet), fixedNow))
    }

    @Test
    fun asksOnlyOnce() = runTest {
        val (consent, _) = consent()
        consent.arrivalNotified()

        consent.promptShown(daysSinceInstall = 3, notificationAuth = NotificationAuth.AUTHORIZED)

        assertFalse(consent.shouldPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = fixedNow))
        assertEquals(
            mapOf("days_since_install" to 3L, "notification_auth" to "authorized"),
            analytics.values(AnalyticsEvent.PROMOTIONS_PROMPT_SHOWN),
        )
    }

    @Test
    fun doesNotAskWhenAlreadyOn() = runTest {
        val (consent, _) = consent()
        consent.arrivalNotified()
        consent.set(true, PromotionsConsent.Source.SETTINGS)?.join()

        assertFalse(consent.shouldPrompt(NotificationAuth.AUTHORIZED, lastArrivalOpenedAt = null, now = fixedNow))
    }

    @Test
    fun acceptingTurnsConsentOnAndTellsTheServer() = runTest {
        val (consent, preferences) = consent()

        consent.answerPrompt(PromotionsConsent.Answer.ACCEPTED, Duration.ofSeconds(4))?.join()

        assertTrue(preferences.promotions.first().consent)
        assertTrue(devices.registered.single().promotionsConsent)
        assertEquals(
            mapOf("result" to "accepted", "duration_s" to 4L),
            analytics.values(AnalyticsEvent.PROMOTIONS_PROMPT_ANSWERED),
        )
        assertEquals(
            mapOf("to" to "on", "source" to "first_arrival"),
            analytics.values(AnalyticsEvent.PROMOTIONS_CONSENT_CHANGED),
        )
    }

    @Test
    fun decliningOrDismissingLeavesConsentOff() = runTest {
        val (consent, preferences) = consent()

        assertNull(consent.answerPrompt(PromotionsConsent.Answer.DECLINED, Duration.ofSeconds(2)))
        assertNull(consent.answerPrompt(PromotionsConsent.Answer.DISMISSED, Duration.ofSeconds(2)))

        assertFalse(preferences.promotions.first().consent)
        assertEquals(0, analytics.count(AnalyticsEvent.PROMOTIONS_CONSENT_CHANGED))
        assertTrue(devices.registered.isEmpty())
    }

    @Test
    fun reportsOnlyActualChanges() = runTest {
        val (consent, _) = consent()

        consent.set(true, PromotionsConsent.Source.SETTINGS)?.join()
        assertNull(consent.set(true, PromotionsConsent.Source.SETTINGS))
        consent.set(false, PromotionsConsent.Source.SETTINGS)?.join()

        assertFalse(consent.isOn.first())
        assertEquals(2, analytics.count(AnalyticsEvent.PROMOTIONS_CONSENT_CHANGED))
        assertEquals("0", analytics.userProperties[AnalyticsUserProperty.PROMOTIONS_CONSENT])
        assertEquals(listOf(true, false), devices.registered.map { it.promotionsConsent })
    }

    private fun TestScope.consent(): Pair<PromotionsConsent, AppPreferences> {
        val preferences = testPreferences(folder.root, backgroundScope)
        val registration = DeviceRegistration(
            devices,
            fakeAuthenticator(),
            FakeInstallationIdSource("installation-1"),
            preferences,
            FakeDisplayLanguage(),
        )
        return PromotionsConsent(preferences, registration, analytics, this) to preferences
    }
}
