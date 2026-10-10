package com.locatedo.locatedo.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.locatedo.locatedo.core.common.SystemSettings

// Asking for fine location again shows the system's "change to precise" dialog the first time; after that the app's
// settings are the way, since a turned-down dialog stops showing without telling the app.
@Composable
fun rememberPreciseLocationRequest(hasRequested: Boolean, onRequested: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onRequested()
    }
    return {
        if (hasRequested) {
            SystemSettings.openAppDetails(context)
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}
