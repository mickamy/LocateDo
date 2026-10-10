package com.locatedo.locatedo.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R

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
