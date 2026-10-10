package com.locatedo.locatedo.screens.settings

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.AnalyticsConsent
import com.locatedo.locatedo.core.analytics.AnalyticsConsentState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UsageDataViewModel @Inject constructor(private val consent: AnalyticsConsent) : ViewModel() {
    val state: StateFlow<AnalyticsConsentState?> =
        consent.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun set(isOn: Boolean) {
        viewModelScope.launch { consent.set(isOn, AnalyticsConsent.Source.SETTINGS) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

// Shown in the EEA and the UK, and wherever an answer was given, so it can still be changed after a move.
@Composable
fun UsageDataSection(viewModel: UsageDataViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var isConfirmingOff by rememberSaveable { mutableStateOf(false) }
    val current = state
    if (current == null || !current.showsSetting) {
        return
    }
    SectionHeader(stringResource(R.string.settings_privacy_title))
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_privacy_usage_data_title)) },
        modifier = Modifier.toggleable(
            value = current.isSending,
            role = Role.Switch,
            onValueChange = { isOn ->
                if (isOn) {
                    viewModel.set(true)
                } else {
                    isConfirmingOff = true
                }
            },
        ),
        supportingContent = { Text(stringResource(R.string.settings_privacy_usage_data_footer)) },
        leadingContent = { Icon(Icons.Filled.BarChart, contentDescription = null) },
        trailingContent = { Switch(checked = current.isSending, onCheckedChange = null) },
    )
    HorizontalDivider()
    // Plain buttons and one tap to confirm, so withdrawing stays as easy as agreeing.
    if (isConfirmingOff) {
        AlertDialog(
            onDismissRequest = { isConfirmingOff = false },
            title = { Text(stringResource(R.string.settings_privacy_usage_data_off_title)) },
            text = { Text(stringResource(R.string.settings_privacy_usage_data_off_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isConfirmingOff = false
                        viewModel.set(false)
                    },
                ) {
                    Text(stringResource(R.string.settings_privacy_usage_data_off))
                }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingOff = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
