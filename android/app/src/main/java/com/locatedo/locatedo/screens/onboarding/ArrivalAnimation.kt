package com.locatedo.locatedo.screens.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class ArrivalPhase { WALKING, ARRIVED, NOTIFIED }

private val ParkColor = Color(0xFF34A853)
private val WalkerColor = Color(0xFF1A73E8)

// The moment the app is for, drawn rather than mapped: you walk down the street into the store's circle, and the
// notification arrives. It plays on a loop; with animations turned off it stays on the arrival.
@Composable
fun ArrivalAnimation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val progress = remember { Animatable(0f) }
    var phase by remember { mutableStateOf(ArrivalPhase.WALKING) }

    LaunchedEffect(Unit) {
        val animations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (animations == 0f) {
            progress.snapTo(1f)
            phase = ArrivalPhase.NOTIFIED
            return@LaunchedEffect
        }
        while (true) {
            progress.snapTo(0f)
            phase = ArrivalPhase.WALKING
            delay(START_DELAY_MILLIS)
            progress.animateTo(1f, tween(WALK_MILLIS, easing = FastOutSlowInEasing))
            phase = ArrivalPhase.ARRIVED
            delay(ARRIVED_MILLIS)
            phase = ArrivalPhase.NOTIFIED
            delay(NOTIFIED_MILLIS)
            phase = ArrivalPhase.WALKING
            delay(RESET_MILLIS)
        }
    }

    val circleScale by animateFloatAsState(
        targetValue = if (phase == ArrivalPhase.WALKING) 1f else 1.08f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "store circle",
    )
    val circleAlpha by animateFloatAsState(
        targetValue = if (phase == ArrivalPhase.WALKING) 0.12f else 0.28f,
        label = "store fill",
    )
    val colors = MaterialTheme.colorScheme
    val cart = rememberVectorPainter(Icons.Filled.ShoppingCart)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .clearAndSetSemantics {},
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(colors.surfaceContainerHigh)
            drawPark()
            drawRoads(colors.surface)
            val road = size.height * 0.68f
            val store = Offset(size.width * 0.74f, road)
            drawStore(store, colors.primary, circleScale, circleAlpha)
            translate(store.x - 9.dp.toPx(), store.y - 9.dp.toPx()) {
                with(cart) {
                    draw(Size(18.dp.toPx(), 18.dp.toPx()), colorFilter = ColorFilter.tint(colors.onPrimary))
                }
            }
            val start = size.width * 0.06f
            val end = store.x - 24.dp.toPx()
            drawWalker(Offset(start + (end - start) * progress.value, road))
        }
        SampleArrivalNotification(
            isShown = phase == ArrivalPhase.NOTIFIED,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp),
        )
    }
}

private fun DrawScope.drawPark() {
    val parkSize = Size(size.width * 0.3f, size.height * 0.28f)
    drawRoundRect(
        color = ParkColor.copy(alpha = 0.18f),
        topLeft = Offset(size.width * 0.18f - parkSize.width / 2, size.height * 0.36f - parkSize.height / 2),
        size = parkSize,
        cornerRadius = CornerRadius(12.dp.toPx()),
    )
}

private fun DrawScope.drawRoads(color: Color) {
    val width = 16.dp.toPx()
    val road = size.height * 0.68f
    drawLine(color, Offset(0f, road), Offset(size.width, road), strokeWidth = width)
    val cross = size.width * 0.44f
    drawLine(color, Offset(cross, 0f), Offset(cross, size.height), strokeWidth = width)
}

private fun DrawScope.drawStore(center: Offset, color: Color, circleScale: Float, circleAlpha: Float) {
    scale(circleScale, pivot = center) {
        val radius = 52.dp.toPx()
        drawCircle(color.copy(alpha = circleAlpha), radius, center)
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
        )
    }
    drawCircle(color, 20.dp.toPx(), center)
}

private fun DrawScope.drawWalker(center: Offset) {
    drawCircle(Color.Black.copy(alpha = 0.15f), 12.dp.toPx(), center + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, 11.5.dp.toPx(), center)
    drawCircle(WalkerColor, 8.5.dp.toPx(), center)
}

private const val START_DELAY_MILLIS = 400L
private const val WALK_MILLIS = 2_200
private const val ARRIVED_MILLIS = 300L
private const val NOTIFIED_MILLIS = 3_000L
private const val RESET_MILLIS = 500L
