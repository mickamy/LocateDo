package com.locatedo.locatedo.feature.settings

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.DailyState
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.location.LocationRepository
import com.locatedo.locatedo.core.permissions.PermissionsRepository
import com.locatedo.locatedo.core.push.DisplayLanguage
import com.locatedo.locatedo.core.support.SupportDiagnostics
import com.locatedo.locatedo.core.support.SupportMail
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class ContactSupportViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authenticator: Authenticator,
    private val entitlements: Entitlements,
    private val syncStateRepository: SyncStateRepository,
    private val permissions: PermissionsRepository,
    private val locationRepository: LocationRepository,
    private val displayLanguage: DisplayLanguage,
    private val analytics: Analytics,
) : ViewModel() {
    suspend fun diagnostics(): SupportDiagnostics {
        val granted = permissions.observe().first()
        return SupportDiagnostics(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            osVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            language = displayLanguage.current(),
            timeZone = ZoneId.systemDefault().id,
            supportId = analytics.appInstanceId(),
            userId = authenticator.current()?.userId,
            plan = DailyState.plan(entitlements.subscription.value, syncStateRepository.get().plan),
            locationAuth = granted.location,
            preciseLocation = locationRepository.hasPrecisePermission(),
            notificationAuth = granted.notifications,
            batterySaver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            batteryUsage = batteryUsage(),
        )
    }

    private fun batteryUsage(): SupportDiagnostics.BatteryUsage {
        if (permissions.isBatteryOptimizationExempt()) {
            return SupportDiagnostics.BatteryUsage.UNRESTRICTED
        }
        if (context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true) {
            return SupportDiagnostics.BatteryUsage.RESTRICTED
        }
        return SupportDiagnostics.BatteryUsage.OPTIMIZED
    }
}

@Composable
fun ContactSupportItem(viewModel: ContactSupportViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val subject = stringResource(R.string.settings_about_contact_subject)
    val template = stringResource(R.string.settings_about_contact_body)
    var isShowingFailure by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_about_contact)) },
        modifier = Modifier.clickable {
            scope.launch {
                val details = viewModel.diagnostics().text
                val opened = openMail(context, subject, template + details)
                if (!opened) {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(subject, details)))
                    isShowingFailure = true
                }
            }
        },
        leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
    )

    if (isShowingFailure) {
        AlertDialog(
            onDismissRequest = { isShowingFailure = false },
            title = { Text(stringResource(R.string.settings_about_contact_failed_title)) },
            text = { Text(stringResource(R.string.settings_about_contact_failed_message, SupportMail.ADDRESS)) },
            confirmButton = {
                TextButton(onClick = { isShowingFailure = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

// Mail apps differ in whether they read the mailto query or the extras, so both carry the draft.
private fun openMail(context: Context, subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, SupportMail.uri(subject, body).toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(SupportMail.ADDRESS))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
