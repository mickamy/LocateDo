package com.locatedo.locatedo.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.model.Place
import kotlin.math.roundToInt

private const val STEP_METERS = 50f

// The notification radius in 50 m steps, shown in the locale's unit next to the slider.
@Composable
fun RadiusSlider(
    meters: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = meters.toFloat(),
            onValueChange = { value -> onChange(((value / STEP_METERS).roundToInt() * STEP_METERS).toDouble()) },
            modifier = Modifier.weight(1f),
            valueRange = Place.RADIUS_RANGE.start.toFloat()..Place.RADIUS_RANGE.endInclusive.toFloat(),
            steps = ((Place.RADIUS_RANGE.endInclusive - Place.RADIUS_RANGE.start) / STEP_METERS).toInt() - 1,
            onValueChangeFinished = onChangeFinished,
        )
        Spacer(Modifier.width(16.dp))
        Text(DistanceFormatting.string(meters), style = MaterialTheme.typography.bodyLarge)
    }
}
