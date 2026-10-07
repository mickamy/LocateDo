package com.locatedo.locatedo.feature.account

import androidx.activity.compose.LocalActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.auth.GoogleSignIn
import com.locatedo.locatedo.core.auth.GoogleSignInResult
import com.locatedo.locatedo.core.auth.Nonce
import com.locatedo.locatedo.ui.appstatus.LocalAppStatus
import com.locatedo.locatedo.ui.appstatus.MaintenanceNote
import kotlinx.coroutines.launch

// Credential Manager needs the Activity, so the Google sign-in runs from the screen and hands the ViewModel the id
// token; Apple's opens in a browser tab and comes back through MainActivity. The confirmation a sign-in may need (an
// account that already has data) travels with the buttons, so every screen that offers the sign-in can finish it.
@Composable
fun SignInButtons(viewModel: AccountViewModel, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val googleSignIn = remember { GoogleSignIn() }
    val isUnderMaintenance = LocalAppStatus.current.activeMaintenance != null

    Column(modifier = modifier) {
        GoogleSignInButton(
            onClick = {
                val context = activity ?: return@GoogleSignInButton
                scope.launch {
                    val nonce = Nonce.make()
                    val result = try {
                        googleSignIn.signIn(context, nonce)
                    } catch (e: GetCredentialException) {
                        viewModel.signInFailed()
                        return@launch
                    }
                    when (result) {
                        is GoogleSignInResult.IdToken -> viewModel.signInWithGoogle(result.value, nonce)
                        GoogleSignInResult.NoAccount -> viewModel.signInFailed()
                        GoogleSignInResult.Canceled -> Unit
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !isUnderMaintenance,
        )
        if (viewModel.isAppleSignInAvailable) {
            AppleSignInButton(
                onClick = {
                    val context = activity ?: return@AppleSignInButton
                    val url = viewModel.startAppleSignIn() ?: return@AppleSignInButton
                    CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                enabled = enabled && !isUnderMaintenance,
            )
        }
        MaintenanceNote(modifier = Modifier.padding(top = 8.dp))
    }

    if (uiState.needsReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelReplacingLocalData,
            title = { Text(stringResource(R.string.settings_account_android_replace_confirm_title)) },
            text = { Text(stringResource(R.string.settings_account_android_replace_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmReplacingLocalData) {
                    Text(stringResource(R.string.settings_account_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelReplacingLocalData) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// Google's sign-in branding: the four-color "G" with Google's wording, on white with a gray outline, or on near-black
// in dark mode. Like iOS, it is shaped like the Apple button below it.
@Composable
private fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var container = Color.White
    var content = GoogleLightText
    var outline = GoogleLightOutline
    if (isSystemInDarkTheme()) {
        container = GoogleDarkContainer
        content = GoogleDarkText
        outline = GoogleDarkOutline
    }
    var alpha = 1f
    if (!enabled) {
        alpha = DISABLED_ALPHA
    }
    Button(
        onClick = onClick,
        modifier = modifier.alpha(alpha),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        border = BorderStroke(1.dp, outline),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.settings_account_android_sign_in), fontWeight = FontWeight.Medium)
    }
}

private val GoogleLightText = Color(0xFF1F1F1F)
private val GoogleLightOutline = Color(0xFF747775)
private val GoogleDarkContainer = Color(0xFF131314)
private val GoogleDarkText = Color(0xFFE3E3E3)
private val GoogleDarkOutline = Color(0xFF8E918F)
private const val DISABLED_ALPHA = 0.5f

// Apple's black style: a white logo and Apple's own wording on black. Apple's artwork is licensed for Apple platforms
// only, so the logo is a CC0 drawing of it.
@Composable
private fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_apple_logo),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_account_android_apple_sign_in))
    }
}
