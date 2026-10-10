package com.locatedo.locatedo.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.screens.appstatus.UpdateRequiredScreen
import com.locatedo.locatedo.screens.onboarding.OnboardingScreen
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus

// Onboarding first, an old build blocked, then the app; and the notices kept for the next time it is open.
@Composable
fun LocateDoApp(viewModel: AppViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appStatus = LocalAppStatus.current
    val pendingInvite by viewModel.pendingInvite.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize())
        appStatus.requiresUpdate -> UpdateRequiredScreen()
        !uiState.hasCompletedOnboarding -> OnboardingScreen(
            pendingInvite = pendingInvite,
            onFinished = { result -> viewModel.onboardingFinished(result.choice, result.placeName) },
        )
        else -> MainScreen(viewModel)
    }
    uiState.notice?.let { notice ->
        val (title, message) = when (notice) {
            AppNotice.REMOVED -> R.string.removed_title to R.string.removed_android_message
            AppNotice.SESSION_ENDED -> R.string.session_ended_title to R.string.session_ended_android_message
        }
        NoticeDialog(title = stringResource(title), message = stringResource(message), onDismiss = viewModel::dismissNotice)
    }
    appStatus.pendingNotice?.let { notice ->
        NoticeDialog(
            title = stringResource(R.string.announcement_title),
            message = notice.message.text(LocalConfiguration.current.locales[0].language).orEmpty(),
            onDismiss = { viewModel.noticeShown(notice) },
        )
    }
}

@Composable
private fun NoticeDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        },
    )
}
