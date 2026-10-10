package com.locatedo.locatedo.core.support

import com.locatedo.locatedo.core.analytics.DailyState
import com.locatedo.locatedo.core.analytics.analyticsKey
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import java.util.UUID

object SupportMail {
    const val ADDRESS = "support@locatedo.com"

    fun uri(subject: String, body: String): String = "mailto:$ADDRESS?subject=${encode(subject)}&body=${encode(body)}"

    private const val UNRESERVED = "-._~"

    private fun encode(value: String): String {
        val encoded = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val char = (byte.toInt() and 0xFF).toChar()
            if (char.isAsciiLetterOrDigit() || char in UNRESERVED) {
                encoded.append(char)
            } else {
                encoded.append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return encoded.toString()
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

data class SupportDiagnostics(
    val appVersion: String,
    val osVersion: String,
    val deviceModel: String,
    val language: String,
    val timeZone: String,
    val supportId: String?,
    val sharesUsageData: Boolean,
    val userId: UUID?,
    val plan: DailyState.PlanState,
    val locationAuth: LocationAuth,
    val preciseLocation: Boolean,
    val notificationAuth: NotificationAuth,
    val batterySaver: Boolean,
    val batteryUsage: BatteryUsage,
) {
    enum class BatteryUsage(val key: String) {
        UNRESTRICTED("unrestricted"),
        OPTIMIZED("optimized"),
        RESTRICTED("restricted"),
    }

    val text: String
        get() {
            val lines = mutableListOf(
                "App: StopBy $appVersion",
                "OS: Android $osVersion ($deviceModel)",
                "Language: $language / Time zone: $timeZone",
            )
            if (supportId != null) {
                lines.add("Support ID: $supportId")
            } else if (!sharesUsageData) {
                // Says why there is no Support ID, rather than leaving it to look like a failure.
                lines.add("Usage data: off")
            }
            if (userId != null) {
                lines.add("User ID: $userId")
            }
            var location = locationAuth.analyticsKey
            if (preciseLocation) {
                location += ", precise"
            } else {
                location += ", approximate"
            }
            lines.add("Plan: ${plan.key}")
            lines.add("Location: $location")
            lines.add("Notifications: ${notificationAuth.analyticsKey}")
            lines.add("Battery saver: ${onOff(batterySaver)}")
            lines.add("Battery usage: ${batteryUsage.key}")
            return lines.joinToString("\n")
        }

    private fun onOff(value: Boolean): String {
        if (value) {
            return "on"
        }
        return "off"
    }
}
