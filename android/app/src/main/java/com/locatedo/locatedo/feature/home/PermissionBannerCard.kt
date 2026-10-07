package com.locatedo.locatedo.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.locatedo.locatedo.R

@Composable
fun PermissionBannerCard(banner: PermissionBanner, onClick: () -> Unit) {
    val context = LocalContext.current
    val backgroundOption = remember(context) { context.packageManager.backgroundPermissionOptionLabel.toString() }
    val message = when (banner) {
        PermissionBanner.LOCATION_ALWAYS -> stringResource(R.string.home_permission_banner_android_location, backgroundOption)
        PermissionBanner.LOCATION_DENIED -> stringResource(R.string.home_permission_banner_location_denied)
        PermissionBanner.NOTIFICATIONS -> stringResource(R.string.home_permission_banner_notifications)
    }
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        ListItem(
            headlineContent = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.clickable(onClick = onClick, role = Role.Button),
            leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                headlineColor = MaterialTheme.colorScheme.onErrorContainer,
                leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        )
    }
}
