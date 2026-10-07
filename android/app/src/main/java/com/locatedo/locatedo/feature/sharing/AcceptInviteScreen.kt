package com.locatedo.locatedo.feature.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.feature.account.AccountFailure
import com.locatedo.locatedo.feature.account.AccountViewModel
import com.locatedo.locatedo.feature.account.GoogleSignInButton
import com.locatedo.locatedo.ui.analytics.TrackScreen
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus
import com.locatedo.locatedo.ui.appstatus.MaintenanceNote
import kotlinx.coroutines.launch

// The link arrives pasted or from App Links; joining needs an account, so the signed-out form offers the sign-in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptInviteScreen(
    token: String?,
    onBack: () -> Unit,
    onJoined: () -> Unit,
    viewModel: AcceptInviteViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    TrackScreen(AnalyticsScreen.ACCEPT_INVITE)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.start(token)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AcceptInviteEvent.Joined -> onJoined()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.invite_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.link,
                onValueChange = viewModel::setLink,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.invite_link_placeholder)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val pasted = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.coerceToText(context)?.toString()
                                if (!pasted.isNullOrBlank()) {
                                    viewModel.setLink(pasted)
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.invite_android_paste))
                    }
                },
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.invite_android_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.failure?.let { failure ->
                Text(
                    text = stringResource(failureMessage(failure)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.isSignedIn) {
                val isUnderMaintenance = LocalAppStatus.current.activeMaintenance != null
                Button(onClick = viewModel::join, modifier = Modifier.fillMaxWidth(), enabled = uiState.canJoin && !isUnderMaintenance) {
                    if (uiState.isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.invite_join))
                    }
                }
                MaintenanceNote()
            } else {
                Text(
                    text = stringResource(R.string.sharing_android_sign_in_message),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (accountState.failure == AccountFailure.SIGN_IN) {
                    Text(
                        text = stringResource(R.string.settings_account_sign_in_failed),
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                GoogleSignInButton(viewModel = accountViewModel, modifier = Modifier.fillMaxWidth(), enabled = !accountState.isWorking)
            }
        }
    }
}

private fun failureMessage(failure: InviteFailure): Int = when (failure) {
    InviteFailure.INVALID -> R.string.invite_invalid
    InviteFailure.UNAVAILABLE -> R.string.invite_unavailable
    InviteFailure.ALREADY_MEMBER -> R.string.invite_already_member
    InviteFailure.LEAVE_FIRST -> R.string.invite_leave_first
    InviteFailure.FAILED -> R.string.invite_failed
}
