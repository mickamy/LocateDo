package com.locatedo.locatedo.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.ui.analytics.TrackScreen

// Explains why arrival reminders need background location (Play's prominent disclosure), then sends the user to the
// system page where "all the time" can be chosen; Android 11 and later offer it nowhere else.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlwaysLocationSheet(onAnswer: (AlwaysPromptAnswer) -> Unit, onDismiss: () -> Unit) {
    TrackScreen(AnalyticsScreen.ALWAYS_LOCATION_PROMPT)
    val context = LocalContext.current
    val backgroundOption = remember(context) { context.packageManager.backgroundPermissionOptionLabel.toString() }
    val requestBackgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = {
            onAnswer(AlwaysPromptAnswer.DISMISSED)
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.always_prompt_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.permission_android_background),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.always_prompt_android_choose, backgroundOption),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    onAnswer(AlwaysPromptAnswer.ALLOW)
                    requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_open_settings))
            }
            TextButton(
                onClick = {
                    onAnswer(AlwaysPromptAnswer.LATER)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.always_prompt_later))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
