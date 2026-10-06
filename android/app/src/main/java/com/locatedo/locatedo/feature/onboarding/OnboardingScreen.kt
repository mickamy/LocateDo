package com.locatedo.locatedo.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsParameter
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.ui.analytics.TrackScreen

// One page per permission, like Google Maps' first-run location screen: what the app does, then the system dialog.
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
                OnboardingStep.INTRO -> Intro(
                    isRequesting = isRequesting,
                    onStart = {
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
            }
        }
    }
}

@Composable
private fun ColumnScope.Intro(isRequesting: Boolean, onStart: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Hero(Icons.Filled.Place)
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
    Text(
        text = stringResource(R.string.onboarding_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.onboarding_permissions),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), enabled = !isRequesting) {
        Text(stringResource(R.string.onboarding_start))
    }
}

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
private fun Hero(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(88.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}
