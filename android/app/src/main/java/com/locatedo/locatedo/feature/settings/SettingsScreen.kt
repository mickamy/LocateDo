package com.locatedo.locatedo.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.PermissionAction
import com.locatedo.locatedo.core.analytics.PermissionKind
import com.locatedo.locatedo.core.billing.PlanKind
import com.locatedo.locatedo.core.common.LegalLinks
import com.locatedo.locatedo.core.common.SystemSettings
import com.locatedo.locatedo.core.permissions.LocationAuth
import com.locatedo.locatedo.core.permissions.NotificationAuth
import com.locatedo.locatedo.feature.onboarding.AlwaysLocationSheet
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.components.RadiusSlider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// The same sections as the iOS settings, as a Material list: permissions, the default radius, categories, about.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit,
    onOpenSharing: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.SETTINGS)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    val isExplainingAlwaysLocation by viewModel.isExplainingAlwaysLocation.collectAsStateWithLifecycle()
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.sharing_title)) },
                modifier = Modifier.clickable(onClick = onOpenSharing),
                leadingContent = { Icon(Icons.Filled.Group, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            )
            HorizontalDivider()
            LocationSection(
                auth = uiState.location,
                onAllow = {
                    viewModel.permissionActionTapped(PermissionKind.LOCATION, PermissionAction.REQUEST)
                    requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onExplainAlways = viewModel::explainAlwaysLocation,
                onOpenSettings = {
                    viewModel.permissionActionTapped(PermissionKind.LOCATION, PermissionAction.OPEN_SETTINGS)
                    SystemSettings.openAppDetails(context)
                },
            )
            HorizontalDivider()
            NotificationSection(
                auth = uiState.notifications,
                promotionsConsent = uiState.promotionsConsent,
                onPromotionsConsentChange = viewModel::setPromotionsConsent,
                onAllow = {
                    viewModel.permissionActionTapped(PermissionKind.NOTIFICATIONS, PermissionAction.REQUEST)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenSettings = {
                    viewModel.permissionActionTapped(PermissionKind.NOTIFICATIONS, PermissionAction.OPEN_SETTINGS)
                    SystemSettings.openNotifications(context)
                },
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
            ProSection(
                state = uiState.pro,
                locale = locale,
                onUpgrade = viewModel::upgrade,
                onManage = { uriHandler.openUri(LegalLinks.PLAY_SUBSCRIPTIONS) },
                onRestore = viewModel::restorePurchases,
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
            onAnswer = viewModel::alwaysLocationAnswered,
            onDismiss = viewModel::dismissAlwaysLocation,
        )
    }
    if (uiState.pro.restoreResult == RestoreResult.RESTORED) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreResult,
            title = { Text(stringResource(R.string.paywall_restored_title)) },
            text = { Text(stringResource(R.string.paywall_restored_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRestoreResult) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

// Mirrors the iOS Pro section: the status, what the store says about the subscription, and the ways to change it.
@Composable
private fun ProSection(state: ProUiState, locale: Locale, onUpgrade: () -> Unit, onManage: () -> Unit, onRestore: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_pro_title)) },
        leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
        trailingContent = {
            Text(stringResource(if (state.isPro) R.string.settings_pro_active else R.string.settings_pro_free))
        },
    )
    for (detail in state.details) {
        ProDetailRow(detail = detail, locale = locale)
    }
    if (state.hasEntitlement) {
        SectionAction(stringResource(R.string.settings_pro_manage), onManage)
    } else if (state.canUpgrade) {
        SectionAction(stringResource(R.string.settings_pro_upgrade), onUpgrade)
    }
    if (state.canRestore) {
        if (state.isRestoring) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            SectionAction(stringResource(R.string.paywall_restore), onRestore)
        }
    }
    when (state.restoreResult) {
        RestoreResult.NOTHING -> SectionNote(stringResource(R.string.paywall_nothing_to_restore))
        RestoreResult.FAILED -> SectionNote(stringResource(R.string.paywall_failed))
        RestoreResult.RESTORED, null -> Unit
    }
}

@Composable
private fun ProDetailRow(detail: ProDetail, locale: Locale) {
    when (detail) {
        is ProDetail.Term -> {
            val term = stringResource(if (detail.kind == PlanKind.ANNUAL) R.string.settings_pro_annual else R.string.settings_pro_monthly)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_pro_term)) },
                trailingContent = { Text(if (detail.isTrial) stringResource(R.string.settings_pro_trial, term) else term) },
            )
        }
        is ProDetail.Renews -> ListItem(
            headlineContent = { Text(stringResource(R.string.settings_pro_renews)) },
            trailingContent = { Text(formatDate(detail.at, locale)) },
        )
        is ProDetail.Ends -> ListItem(
            headlineContent = { Text(stringResource(R.string.settings_pro_ends)) },
            trailingContent = { Text(formatDate(detail.at, locale)) },
        )
        ProDetail.AutoRenewOff -> SectionNote(stringResource(R.string.settings_pro_auto_renew_off))
        ProDetail.BillingIssue -> Text(
            text = stringResource(R.string.settings_pro_billing_issue),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        ProDetail.Household -> SectionNote(stringResource(R.string.settings_pro_household))
    }
}

private fun formatDate(at: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault()).format(at)

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
private fun NotificationSection(
    auth: NotificationAuth,
    promotionsConsent: Boolean,
    onPromotionsConsentChange: (Boolean) -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
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
    // Usable without the notification permission; the rows above already offer to grant it.
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_notifications_promotions_title)) },
        modifier = Modifier.toggleable(
            value = promotionsConsent,
            role = Role.Switch,
            onValueChange = onPromotionsConsentChange,
        ),
        supportingContent = { Text(stringResource(R.string.settings_notifications_promotions_footer)) },
        leadingContent = { Icon(Icons.Filled.Campaign, contentDescription = null) },
        trailingContent = { Switch(checked = promotionsConsent, onCheckedChange = null) },
    )
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
