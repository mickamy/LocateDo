package com.locatedo.locatedo.core.support

import com.locatedo.locatedo.core.analytics.DailyState
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportMailTest {
    private val diagnostics = SupportDiagnostics(
        appVersion = "1.0.1 (10)",
        osVersion = "16 (API 36)",
        deviceModel = "Google Pixel 9",
        language = "ja",
        timeZone = "Asia/Tokyo",
        supportId = "1a2b3c",
        sharesUsageData = true,
        userId = UUID.fromString("0198f2a4-1c3b-7d2e-9f00-0123456789ab"),
        plan = DailyState.PlanState.TRIAL,
        locationAuth = LocationAuth.ALWAYS,
        preciseLocation = true,
        notificationAuth = NotificationAuth.AUTHORIZED,
        batterySaver = false,
        batteryUsage = SupportDiagnostics.BatteryUsage.UNRESTRICTED,
    )

    @Test
    fun listsEveryDetail() {
        val expected = """
            App: StopBy 1.0.1 (10)
            OS: Android 16 (API 36) (Google Pixel 9)
            Language: ja / Time zone: Asia/Tokyo
            Support ID: 1a2b3c
            User ID: 0198f2a4-1c3b-7d2e-9f00-0123456789ab
            Plan: trial
            Location: always, precise
            Notifications: authorized
            Battery saver: off
            Battery usage: unrestricted
        """.trimIndent()

        assertEquals(expected, diagnostics.text)
    }

    @Test
    fun leavesOutMissingIds() {
        val signedOut = diagnostics.copy(
            supportId = null,
            userId = null,
            preciseLocation = false,
            batterySaver = true,
            batteryUsage = SupportDiagnostics.BatteryUsage.RESTRICTED,
        )

        assertFalse(signedOut.text.contains("Support ID"))
        assertFalse(signedOut.text.contains("User ID"))
        assertTrue(signedOut.text.contains("Location: always, approximate"))
        assertTrue(signedOut.text.contains("Battery saver: on"))
        assertTrue(signedOut.text.contains("Battery usage: restricted"))
        assertFalse(signedOut.text.contains("Usage data"))
    }

    @Test
    fun saysUsageDataIsOffInsteadOfTheSupportId() {
        val declined = diagnostics.copy(supportId = null, sharesUsageData = false)

        assertFalse(declined.text.contains("Support ID"))
        assertTrue(declined.text.contains("Usage data: off"))
    }

    @Test
    fun encodesSubjectAndBody() {
        val uri = SupportMail.uri(subject = "StopBy Support", body = "a&b=c+d\n■ 日時：?")

        val expected = "mailto:support@locatedo.com?subject=StopBy%20Support" +
            "&body=a%26b%3Dc%2Bd%0A%E2%96%A0%20%E6%97%A5%E6%99%82%EF%BC%9A%3F"
        assertEquals(expected, uri)
    }
}
