package com.locatedo.locatedo.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.common.LegalLinks
import com.locatedo.locatedo.core.common.SystemSettings
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.feature.onboarding.AlwaysLocationSheet
import com.locatedo.locatedo.ui.components.RadiusSlider

// The same sections as the iOS settings, as a Material list: permissions, the default radius, categories, about.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    var isExplainingAlwaysLocation by remember { mutableStateOf(false) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.locationRequested()
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.notificationsRequested()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (uiState.isLoading) {
                return@Scaffold
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_account_title)) },
                modifier = Modifier.clickable(onClick = onOpenAccount),
                supportingContent = {
                    val status = if (uiState.isSignedIn) {
                        R.string.settings_account_android_signed_in
                    } else {
                        R.string.settings_account_not_signed_in
                    }
                    Text(stringResource(status))
                },
                leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
            HorizontalDivider()
            LocationSection(
                auth = uiState.location,
                onAllow = {
                    requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onExplainAlways = { isExplainingAlwaysLocation = true },
                onOpenSettings = { SystemSettings.openAppDetails(context) },
            )
            HorizontalDivider()
            NotificationSection(
                auth = uiState.notifications,
                onAllow = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenSettings = { SystemSettings.openNotifications(context) },
            )
            HorizontalDivider()
            DefaultRadiusSection(meters = uiState.defaultRadiusMeters, onSave = viewModel::setDefaultRadius)
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.category_title)) },
                modifier = Modifier.clickable(onClick = onOpenCategories),
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_about_title))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_version)) },
                trailingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_privacy_policy)) },
                modifier = Modifier.clickable {
                    uriHandler.openUri(LegalLinks.privacyPolicy(locale))
                },
                leadingContent = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
            )
        }
    }

    if (isExplainingAlwaysLocation) {
        AlwaysLocationSheet(
            onDismiss = {
                isExplainingAlwaysLocation = false
                viewModel.refreshPermissions()
            },
        )
    }
}

@Composable
private fun LocationSection(
    auth: LocationAuth,
    onAllow: () -> Unit,
    onExplainAlways: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val backgroundOption = remember(context) { context.packageManager.backgroundPermissionOptionLabel.toString() }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_location_title)) },
        leadingContent = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        trailingContent = { Text(locationStatus(auth)) },
    )
    when (auth) {
        LocationAuth.ALWAYS -> Unit
        LocationAuth.WHEN_IN_USE -> {
            SectionNote(stringResource(R.string.settings_location_android_needs_always, backgroundOption))
            SectionAction(stringResource(R.string.settings_open_settings), onExplainAlways)
        }
        LocationAuth.NOT_DETERMINED -> {
            SectionNote(stringResource(R.string.settings_location_android_needs_always, backgroundOption))
            SectionAction(stringResource(R.string.settings_location_allow), onAllow)
        }
        LocationAuth.DENIED -> {
            SectionNote(stringResource(R.string.settings_location_android_needs_always, backgroundOption))
            SectionAction(stringResource(R.string.settings_open_settings), onOpenSettings)
        }
    }
}

@Composable
private fun NotificationSection(auth: NotificationAuth, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_notifications_title)) },
        leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
        trailingContent = { Text(notificationStatus(auth)) },
    )
    when (auth) {
        NotificationAuth.AUTHORIZED -> Unit
        NotificationAuth.NOT_DETERMINED -> {
            SectionNote(stringResource(R.string.settings_notifications_needs_allow))
            SectionAction(stringResource(R.string.settings_notifications_allow), onAllow)
        }
        NotificationAuth.DENIED -> {
            SectionNote(stringResource(R.string.settings_notifications_needs_allow))
            SectionAction(stringResource(R.string.settings_open_settings), onOpenSettings)
        }
    }
}

// The slider edits a local copy and writes once the thumb is released.
@Composable
private fun DefaultRadiusSection(meters: Double, onSave: (Double) -> Unit) {
    var radius by remember(meters) { mutableDoubleStateOf(meters) }
    SectionHeader(stringResource(R.string.settings_default_radius_title))
    RadiusSlider(
        meters = radius,
        onChange = { radius = it },
        label = stringResource(R.string.settings_default_radius_label),
        modifier = Modifier.padding(horizontal = 16.dp),
        onChangeFinished = { onSave(radius) },
    )
    SectionNote(stringResource(R.string.settings_default_radius_label))
}

@Composable
private fun locationStatus(auth: LocationAuth): String = when (auth) {
    LocationAuth.ALWAYS -> stringResource(R.string.settings_location_always)
    LocationAuth.WHEN_IN_USE -> stringResource(R.string.settings_location_when_in_use)
    LocationAuth.DENIED -> stringResource(R.string.settings_location_denied)
    LocationAuth.NOT_DETERMINED -> stringResource(R.string.settings_location_not_determined)
}

@Composable
private fun notificationStatus(auth: NotificationAuth): String = when (auth) {
    NotificationAuth.AUTHORIZED -> stringResource(R.string.settings_notifications_authorized)
    NotificationAuth.DENIED -> stringResource(R.string.settings_notifications_denied)
    NotificationAuth.NOT_DETERMINED -> stringResource(R.string.settings_notifications_not_determined)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label)
    }
}
