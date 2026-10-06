package com.locatedo.locatedo.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsParameters
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.NoOpAnalytics

val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoOpAnalytics }

// Logged when the screen enters the composition, and again when its parameters change.
@Composable
fun TrackScreen(screen: AnalyticsScreen, parameters: AnalyticsParameters = emptyMap()) {
    val analytics = LocalAnalytics.current
    LaunchedEffect(screen, parameters) {
        analytics.logScreen(screen, parameters)
    }
}
