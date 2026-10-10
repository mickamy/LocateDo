package com.locatedo.locatedo.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AlwaysPromptAnswer
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.SystemSettings
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.core.permissions.Permissions
import com.locatedo.locatedo.ui.analytics.TrackScreen

// What arrival reminders still need, offered right after a place is saved: notifications, "all the time" location, and
// precise location when it was approximate, each with a check once done. The background location text stays as Play's
// prominent disclosure.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSetupSheet(
    permissions: Permissions,
    asksForPreciseLocation: Boolean,
    onNotificationsRequested: () -> Unit,
    onPreciseLocationRequested: () -> Unit,
    onRefresh: () -> Unit,
    onClose: (AlwaysPromptAnswer) -> Unit,
) {
    TrackScreen(AnalyticsScreen.ALWAYS_LOCATION_PROMPT)
    val context = LocalContext.current
    val backgroundOption = remember(context) { context.packageManager.backgroundPermissionOptionLabel.toString() }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onNotificationsRequested()
    }
    val requestBackgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onRefresh()
    }
    val requestPreciseLocation = rememberPreciseLocationRequest(permissions.hasRequestedPreciseLocation, onPreciseLocationRequested)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onRefresh()
    }

    ModalBottomSheet(
        onDismissRequest = { onClose(AlwaysPromptAnswer.DISMISSED) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
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
                text = stringResource(R.string.reminder_setup_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.reminder_setup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val canRequestNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    permissions.notifications == NotificationAuth.NOT_DETERMINED
                SetupRow(
                    title = stringResource(R.string.reminder_setup_notifications),
                    icon = Icons.Filled.Notifications,
                    done = if (ReminderSetup.needsNotifications(permissions)) null else stringResource(R.string.reminder_setup_allowed),
                    action = if (canRequestNotifications) {
                        stringResource(R.string.reminder_setup_allow)
                    } else {
                        stringResource(R.string.settings_open_settings)
                    },
                    onAction = {
                        if (canRequestNotifications) {
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            SystemSettings.openNotifications(context)
                        }
                    },
                )
                SetupRow(
                    title = stringResource(R.string.reminder_setup_location),
                    icon = Icons.Filled.LocationOn,
                    done = if (ReminderSetup.needsAlways(permissions)) null else stringResource(R.string.settings_location_always),
                    action = stringResource(R.string.settings_open_settings),
                    onAction = {
                        if (permissions.location == LocationAuth.WHEN_IN_USE) {
                            requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            SystemSettings.openAppDetails(context)
                        }
                    },
                )
                if (asksForPreciseLocation) {
                    SetupRow(
                        title = stringResource(R.string.reminder_setup_precise_location),
                        icon = Icons.Filled.GpsFixed,
                        done = if (permissions.needsPreciseLocation) null else stringResource(R.string.reminder_setup_turned_on),
                        action = if (permissions.hasRequestedPreciseLocation) {
                            stringResource(R.string.settings_open_settings)
                        } else {
                            stringResource(R.string.reminder_setup_allow)
                        },
                        onAction = requestPreciseLocation,
                    )
                }
            }
            if (ReminderSetup.needsAlways(permissions)) {
                Text(
                    text = stringResource(R.string.permission_android_background),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.always_prompt_android_choose, backgroundOption),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            PrivacyNote(stringResource(R.string.always_prompt_privacy))
            OutlinedButton(onClick = { onClose(AlwaysPromptAnswer.LATER) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.reminder_setup_later))
            }
            TextButton(onClick = { onClose(AlwaysPromptAnswer.NEVER) }) {
                Text(
                    text = stringResource(R.string.reminder_setup_never),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupRow(title: String, icon: ImageVector, done: String?, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (done != null) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = done, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onAction) {
                Text(action)
            }
        }
    }
}
