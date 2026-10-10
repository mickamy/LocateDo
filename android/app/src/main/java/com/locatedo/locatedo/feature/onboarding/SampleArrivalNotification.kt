package com.locatedo.locatedo.feature.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R

// What the app is for, at a glance: the notification that arrives at the store, drawn like one in the shade. It
// drops in once `isShown` turns on, keeping its space so nothing below moves.
@Composable
fun SampleArrivalNotification(isShown: Boolean, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (isShown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "sample notification",
    )
    val drop = with(LocalDensity.current) { 24.dp.toPx() }
    ArrivalNotificationCard(
        title = stringResource(R.string.onboarding_sample_place),
        message = stringResource(R.string.onboarding_sample_todos),
        modifier = modifier.graphicsLayer {
            alpha = progress.coerceIn(0f, 1f)
            translationY = (progress - 1f) * drop
        },
    )
}
