package com.locatedo.locatedo.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.appstatus.AppStatusDocument
import com.locatedo.locatedo.core.appstatus.AppStatusState
import com.locatedo.locatedo.core.appstatus.MaintenancePhase
import com.locatedo.locatedo.logic.MaintenanceText
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus

private val MaintenanceOrange = Color(0xFFE8710A)

// A maintenance window, while announced (dismissible) or running (not).
@Composable
fun MaintenanceBanner(status: AppStatusState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    when (val phase = status.maintenancePhase) {
        is MaintenancePhase.Upcoming -> {
            if (status.showsUpcomingBanner) {
                val start = MaintenanceText.start(phase.maintenance, locale)
                val end = MaintenanceText.end(phase.maintenance, locale)
                Banner(
                    maintenance = phase.maintenance,
                    text = stringResource(R.string.maintenance_upcoming, start, end),
                    onDismiss = onDismiss,
                    modifier = modifier,
                )
            }
        }
        is MaintenancePhase.Active -> Banner(
            maintenance = phase.maintenance,
            text = stringResource(R.string.maintenance_active, MaintenanceText.end(phase.maintenance, locale)),
            onDismiss = null,
            modifier = modifier,
        )
        MaintenancePhase.None -> Unit
    }
}

@Composable
private fun Banner(
    maintenance: AppStatusDocument.Maintenance,
    text: String,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val language = LocalConfiguration.current.locales[0].language
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaintenanceOrange.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Build, contentDescription = null, tint = MaintenanceOrange)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall)
                maintenance.message?.text(language)?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.maintenance_dismiss))
                }
            }
        }
    }
}

// Under a button that cannot work while the server is under maintenance.
@Composable
fun MaintenanceNote(modifier: Modifier = Modifier) {
    val maintenance = LocalAppStatus.current.activeMaintenance ?: return
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = stringResource(R.string.maintenance_unavailable, MaintenanceText.end(maintenance, locale)),
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaintenanceOrange,
    )
}
