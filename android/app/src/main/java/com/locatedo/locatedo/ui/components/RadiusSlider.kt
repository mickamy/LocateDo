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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.model.Place
import kotlin.math.roundToInt

private const val STEP_METERS = 50f

// The notification radius in 50 m steps, shown (and read out) in the locale's unit.
@Composable
fun RadiusSlider(
    meters: Double,
    onChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onChangeFinished: () -> Unit = {},
) {
    val distance = DistanceFormatting.string(meters)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = meters.toFloat(),
            onValueChange = { value -> onChange(((value / STEP_METERS).roundToInt() * STEP_METERS).toDouble()) },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = distance
                },
            valueRange = Place.RADIUS_RANGE.start.toFloat()..Place.RADIUS_RANGE.endInclusive.toFloat(),
            steps = ((Place.RADIUS_RANGE.endInclusive - Place.RADIUS_RANGE.start) / STEP_METERS).toInt() - 1,
            onValueChangeFinished = onChangeFinished,
        )
        Spacer(Modifier.width(16.dp))
        Text(distance, style = MaterialTheme.typography.bodyLarge)
    }
}
