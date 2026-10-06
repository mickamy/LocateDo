package com.locatedo.locatedo.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.R

// Placeholder until the settings sections land; it only shows the version.
@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_version)) },
            trailingContent = { Text(BuildConfig.VERSION_NAME) },
        )
    }
}
