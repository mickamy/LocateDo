package com.locatedo.locatedo.feature.settings

import android.content.ClipData
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Debug and staging builds only, like the iOS DebugSection; the labels are for developers and stay in English.
@Composable
fun DebugSection(viewModel: DebugViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val consentStoreCountry by viewModel.consentStoreCountry.collectAsStateWithLifecycle()

    SectionHeader("Debug")
    DebugRow("Server", uiState.server)
    CopyableRow("User", uiState.userId ?: "-")
    CopyableRow("Household", uiState.householdId ?: "-")
    DebugRow("Cursor", uiState.cursor.toString())
    DebugRow("Queued writes", uiState.queuedWrites.toString())
    DebugRow("Last pull", uiState.lastPull ?: "-")
    TextButton(onClick = viewModel::syncNow, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("Sync now")
    }
    TextButton(onClick = viewModel::checkConnection, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("Check connection")
    }
    if (BuildConfig.DEBUG) {
        LocalNetworkAccess()
    }
    DebugRow("Consent store country", consentStoreCountry ?: "Play account")
    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
        for (country in listOf(null, "GB", "JP")) {
            TextButton(onClick = { viewModel.setConsentStoreCountry(country) }) {
                Text(country ?: "Play account")
            }
        }
    }
    uiState.serverStatus?.let { status ->
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// From API 37 the local server at 10.0.2.2 counts as the local network, which needs a runtime permission; only the
// debug manifest declares it, since staging talks to the remote server.
@Composable
private fun LocalNetworkAccess() {
    val context = LocalContext.current
    var isGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isGranted = granted
    }
    if (!isGranted) {
        TextButton(onClick = { request.launch(LOCAL_NETWORK) }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Allow local network")
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, fontFamily = FontFamily.Monospace) },
    )
}

@Composable
private fun CopyableRow(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, fontFamily = FontFamily.Monospace) },
        trailingContent = {
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                        isCopied = true
                        delay(COPIED_MILLIS)
                        isCopied = false
                    }
                },
            ) {
                if (isCopied) {
                    Icon(Icons.Filled.Check, contentDescription = "Copied", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                }
            }
        },
    )
}

private const val COPIED_MILLIS = 1_500L
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
