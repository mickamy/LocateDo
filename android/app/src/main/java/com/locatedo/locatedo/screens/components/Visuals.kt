package com.locatedo.locatedo.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.CategoryStyle
import com.locatedo.locatedo.core.common.DistanceFormatting
import com.locatedo.locatedo.core.model.Place
import kotlin.math.roundToInt

// The category's icon on a filled circle of its color.
@Composable
fun CategoryBadge(icon: String?, color: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(CategoryStyle.tint(color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            CategoryStyle.icon(icon),
            contentDescription = null,
            modifier = Modifier.size(size * 0.55f),
            tint = Color.White,
        )
    }
}

// The badge with a white rim, drawn onto the map as the place's marker (anchored at its center).
@Composable
fun CategoryMarker(icon: String?, color: String?) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        CategoryBadge(icon = icon, color = color, size = 30.dp)
    }
}

@Composable
fun SwipeToDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.common_delete),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private const val RADIUS_STEP_METERS = 50f

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
            onValueChange = { value -> onChange(((value / RADIUS_STEP_METERS).roundToInt() * RADIUS_STEP_METERS).toDouble()) },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = label
                    stateDescription = distance
                },
            valueRange = Place.RADIUS_RANGE.start.toFloat()..Place.RADIUS_RANGE.endInclusive.toFloat(),
            steps = ((Place.RADIUS_RANGE.endInclusive - Place.RADIUS_RANGE.start) / RADIUS_STEP_METERS).toInt() - 1,
            onValueChangeFinished = onChangeFinished,
        )
        Spacer(Modifier.width(16.dp))
        Text(distance, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BenefitRow(icon: ImageVector, title: Int, message: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Said where location is asked for, since that is when people wonder where it goes.
@Composable
fun PrivacyNote(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            // Breaks Japanese between phrases, so no lone character ends up on the last line.
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase),
                ),
            )
        }
    }
}

// The arrival notification drawn like one in the shade, for showing what one will say. The title and message wrap
// here, so a long place name reads in full.
@Composable
fun ArrivalNotificationCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isMessageMuted: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$title, $message" },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_notification),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column {
                Text(
                    text = "${stringResource(R.string.app_name)} • ${stringResource(R.string.onboarding_sample_time)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                var messageColor = MaterialTheme.colorScheme.onSurface
                if (isMessageMuted) {
                    messageColor = MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = messageColor)
            }
        }
    }
}
