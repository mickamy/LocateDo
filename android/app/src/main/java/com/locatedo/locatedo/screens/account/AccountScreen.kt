package com.locatedo.locatedo.screens.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.navigation.LocalNavigator
import com.locatedo.locatedo.screens.components.BenefitRow
import com.locatedo.locatedo.ui.analytics.TrackScreen

// Signed out: what an account is for, and the Google button. Signed in: sign out and delete.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val onBack = LocalNavigator.current::pop
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.isLoading) {
        TrackScreen(if (uiState.isSignedIn) AnalyticsScreen.ACCOUNT else AnalyticsScreen.ACCOUNT_BENEFITS)
    }
    var isConfirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_done))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isSignedIn) {
            SignedIn(
                state = uiState,
                modifier = Modifier.padding(padding),
                onSignOut = viewModel::prepareSignOut,
                onDelete = { isConfirmingDelete = true },
            )
        } else {
            SignedOut(
                state = uiState,
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
            )
        }
    }

    if (uiState.isConfirmingSignOut) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSignOut,
            title = { Text(stringResource(R.string.settings_account_sign_out_confirm_title)) },
            text = {
                val message = if (uiState.hasUnsyncedWrites) {
                    R.string.settings_account_android_sign_out_unsynced_message
                } else {
                    R.string.settings_account_android_sign_out_confirm_message
                }
                Text(stringResource(message))
            },
            confirmButton = {
                TextButton(onClick = viewModel::signOut) {
                    Text(stringResource(R.string.settings_account_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSignOut) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(R.string.settings_account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_account_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingDelete = false
                        viewModel.deleteAccount()
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun SignedIn(state: AccountScreenState, modifier: Modifier, onSignOut: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_account_signed_in)) },
            leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_account_sign_out)) },
            modifier = Modifier.clickable(enabled = !state.isWorking, onClick = onSignOut),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_account_delete), color = MaterialTheme.colorScheme.error)
            },
            modifier = Modifier.clickable(enabled = !state.isWorking, onClick = onDelete),
        )
        if (state.failure == AccountFailure.DELETE) {
            Text(
                text = stringResource(R.string.settings_account_delete_failed),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SignedOut(state: AccountScreenState, modifier: Modifier, viewModel: AccountViewModel) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.settings_account_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            BenefitRow(
                icon = Icons.Filled.PhoneAndroid,
                title = R.string.settings_account_benefits_sync_title,
                message = R.string.settings_account_benefits_sync_message,
            )
            BenefitRow(
                icon = Icons.Filled.Group,
                title = R.string.settings_account_benefits_share_title,
                message = R.string.settings_account_benefits_share_message,
            )
            BenefitRow(
                icon = Icons.Filled.Lock,
                title = R.string.settings_account_benefits_privacy_title,
                message = R.string.settings_account_benefits_privacy_message,
            )
        }
        if (state.failure == AccountFailure.SIGN_IN) {
            Text(
                text = stringResource(R.string.settings_account_sign_in_failed),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        SignInButtons(viewModel = viewModel, modifier = Modifier.fillMaxWidth(), enabled = !state.isWorking)
        Spacer(Modifier.height(24.dp))
    }
}
