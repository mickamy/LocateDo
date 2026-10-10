package com.locatedo.locatedo.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsParameters
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.NoOpAnalytics
import com.locatedo.locatedo.core.analytics.ScreenEntry
import com.locatedo.locatedo.core.analytics.ScreenTracker

val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }

val LocalScreenTracker = staticCompositionLocalOf { ScreenTracker(NoOpAnalytics) }

// 0 for the screens in the back stack, 1 inside an overlay.
val LocalPresentationLevel = staticCompositionLocalOf { 0 }

val ScreenEntry.parameters: AnalyticsParameters
    get() = mapOf(AnalyticsParameter.ENTRY to key)

// Logged when the screen enters the composition, and again when its parameters change. `opening` goes only with the
// first appearance: a screen in the back stack remembers it across coming back, and an overlay is told apart by
// `openedBy`, the instance that opened it, since it leaves the composition while another screen is pushed.
@Composable
fun TrackScreen(
    screen: AnalyticsScreen,
    parameters: AnalyticsParameters = emptyMap(),
    opening: AnalyticsParameters = emptyMap(),
    openedBy: Any? = null,
) {
    val tracker = LocalScreenTracker.current
    val level = LocalPresentationLevel.current
    var hasAppeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(screen, parameters) {
        var isFirst = !hasAppeared
        if (openedBy != null) {
            isFirst = tracker.isFirstAppearance(openedBy)
        }
        hasAppeared = true
        var sentOnce: AnalyticsParameters = emptyMap()
        if (isFirst) {
            sentOnce = opening
        }
        tracker.appeared(screen, parameters, sentOnce, level)
    }
}
