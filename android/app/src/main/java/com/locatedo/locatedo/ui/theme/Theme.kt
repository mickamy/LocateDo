package com.locatedo.locatedo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The brand blue from the app icon and the iOS app; dynamic color is deliberately not used.
private val Blue = Color(0xFF0A84FF)
private val BlueLight = Color(0xFF3B9BFF)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    onPrimary = Color.White,
)

@Composable
fun LocateDoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
