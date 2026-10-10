package com.locatedo.locatedo.feature.onboarding

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.common.LegalLinks
import com.locatedo.locatedo.ui.analytics.TrackScreen
import kotlinx.coroutines.delay

// What the app does, then why location is safe with it (asking for it there), then notifications.
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    TrackScreen(AnalyticsScreen.ONBOARDING, mapOf(AnalyticsParameter.STEP to step.key))
    var isRequesting by rememberSaveable { mutableStateOf(false) }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        isRequesting = false
        viewModel.locationRequested()
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isRequesting = false
        viewModel.notificationsRequested()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                OnboardingStep.INTRO -> Intro(onStart = viewModel::start)
                OnboardingStep.PRIVACY -> Privacy(
                    isRequesting = isRequesting,
                    onAllow = {
                        isRequesting = true
                        requestLocation.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                )
                OnboardingStep.NOTIFICATIONS -> Notifications(
                    isRequesting = isRequesting,
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            isRequesting = true
                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.skipNotifications()
                        }
                    },
                    onLater = viewModel::skipNotifications,
                )
                OnboardingStep.ANALYTICS -> AnalyticsConsentStep(onAnswer = viewModel::answerAnalytics)
            }
        }
    }
}

private enum class IntroStage { TITLE, NOTIFICATION, FEATURES }

// Top to bottom: the title, then the notification drops in, then what the app does, so the space waiting for the
// notification never reads as a gap.
@Composable
private fun ColumnScope.Intro(onStart: () -> Unit) {
    val context = LocalContext.current
    var stage by rememberSaveable { mutableStateOf(IntroStage.TITLE) }
    LaunchedEffect(Unit) {
        val animations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        if (animations == 0f) {
            stage = IntroStage.FEATURES
            return@LaunchedEffect
        }
        delay(NOTIFICATION_DELAY_MILLIS)
        if (stage < IntroStage.NOTIFICATION) {
            stage = IntroStage.NOTIFICATION
        }
        delay(FEATURES_DELAY_MILLIS)
        stage = IntroStage.FEATURES
    }
    val featuresAlpha by animateFloatAsState(
        targetValue = if (stage >= IntroStage.FEATURES) 1f else 0f,
        animationSpec = tween(FEATURES_FADE_MILLIS),
        label = "features",
    )

    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.onboarding_tagline),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
    SampleArrivalNotification(isShown = stage >= IntroStage.NOTIFICATION, modifier = Modifier.padding(vertical = 8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(featuresAlpha),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Point(Icons.Filled.NotificationsActive, R.string.onboarding_feature_arrival_title, R.string.onboarding_feature_arrival_message)
        Point(Icons.Filled.ShoppingCart, R.string.onboarding_feature_lists_title, R.string.onboarding_feature_lists_message)
        Point(Icons.Filled.Group, R.string.onboarding_feature_family_title, R.string.onboarding_feature_family_message)
    }
    Spacer(Modifier.weight(1f))
    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(featuresAlpha),
    ) {
        Text(stringResource(R.string.onboarding_start))
    }
}

// Right before the system asks for location, which is when people wonder where it goes.
@Composable
private fun ColumnScope.Privacy(isRequesting: Boolean, onAllow: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Hero(Icons.Filled.Lock)
    Text(
        text = stringResource(R.string.onboarding_privacy_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Point(Icons.Filled.PhoneAndroid, R.string.onboarding_privacy_on_device)
        Point(Icons.Filled.CloudOff, R.string.onboarding_privacy_not_sent)
        Point(Icons.Filled.Block, R.string.onboarding_privacy_no_ads)
    }
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.onboarding_permissions),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth(), enabled = !isRequesting) {
        Text(stringResource(R.string.onboarding_allow_location))
    }
}

@Composable
private fun Point(icon: ImageVector, @StringRes title: Int, @StringRes message: Int? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
            if (message == null) {
                Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val NOTIFICATION_DELAY_MILLIS = 400L
private const val FEATURES_DELAY_MILLIS = 500L
private const val FEATURES_FADE_MILLIS = 400

@Composable
private fun ColumnScope.Notifications(isRequesting: Boolean, onAllow: () -> Unit, onLater: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Hero(Icons.Filled.NotificationsActive)
    Text(
        text = stringResource(R.string.onboarding_notifications_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.onboarding_notifications_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth(), enabled = !isRequesting) {
        Text(stringResource(R.string.onboarding_allow_notifications))
    }
    TextButton(onClick = onLater, enabled = !isRequesting) {
        Text(stringResource(R.string.onboarding_later))
    }
}

@Composable
private fun ColumnScope.AnalyticsConsentStep(onAnswer: (Boolean) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    Spacer(Modifier.weight(1f))
    Hero(Icons.Filled.BarChart)
    Text(
        text = stringResource(R.string.onboarding_analytics_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Point(Icons.Filled.Person, R.string.onboarding_analytics_message)
        Point(Icons.Filled.Lock, R.string.onboarding_analytics_not_sent)
        Point(Icons.Filled.Settings, R.string.onboarding_analytics_settings)
    }
    TextButton(onClick = { uriHandler.openUri(LegalLinks.privacyPolicy(locale)) }) {
        Text(stringResource(R.string.settings_about_privacy_policy))
    }
    Spacer(Modifier.weight(1f))
    // Same size and place, so declining is as easy as agreeing.
    Button(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_analytics_allow))
    }
    OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_analytics_deny))
    }
}

@Composable
private fun Hero(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(88.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}
