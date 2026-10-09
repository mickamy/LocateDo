package com.locatedo.locatedo.core.analytics

import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class PermissionKind(val key: String) {
    LOCATION("location"),
    NOTIFICATIONS("notifications"),
}

enum class PermissionAction(val key: String) {
    REQUEST("request"),
    OPEN_SETTINGS("open_settings"),
}

enum class AlwaysPromptAnswer(val key: String) {
    ALLOW("allow"),
    LATER("later"),
    NEVER("never"),
    DISMISSED("dismissed"),
}

fun Analytics.logPermissionAction(kind: PermissionKind, action: PermissionAction) {
    log(
        AnalyticsEvent.PERMISSION_ACTION_TAPPED,
        mapOf(AnalyticsParameter.KIND to kind.key, AnalyticsParameter.ACTION to action.key),
    )
}

// One answer per showing, taken when the user answers: "allow" keeps the sheet open until the system page returns,
// and the close that follows is not a second answer.
class AlwaysPromptTracker(private val analytics: Analytics, private val clock: Clock) {
    private var shownAt: Instant? = null
    private var details: AnalyticsParameters = emptyMap()

    fun shown(details: AnalyticsParameters = emptyMap()) {
        shownAt = clock.instant()
        this.details = details
    }

    fun answered(answer: AlwaysPromptAnswer) {
        val at = shownAt ?: return
        shownAt = null
        analytics.log(
            AnalyticsEvent.ALWAYS_PROMPT_ANSWERED,
            details + mapOf(
                AnalyticsParameter.RESULT to answer.key,
                AnalyticsParameter.DURATION_S to Duration.between(at, clock.instant()).seconds.coerceAtLeast(0),
            ),
        )
    }
}
