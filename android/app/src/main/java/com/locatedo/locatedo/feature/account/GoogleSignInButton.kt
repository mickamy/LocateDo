package com.locatedo.locatedo.feature.account

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.credentials.exceptions.GetCredentialException
import com.locatedo.locatedo.R
import com.locatedo.locatedo.core.auth.GoogleSignIn
import com.locatedo.locatedo.core.auth.GoogleSignInResult
import com.locatedo.locatedo.core.auth.Nonce
import kotlinx.coroutines.launch

// Credential Manager needs the Activity, so the sign-in runs from the screen and hands the ViewModel the id token.
@Composable
fun GoogleSignInButton(viewModel: AccountViewModel, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val googleSignIn = remember { GoogleSignIn() }

    Button(
        onClick = {
            val context = activity ?: return@Button
            scope.launch {
                val nonce = Nonce.make()
                val result = try {
                    googleSignIn.signIn(context, nonce)
                } catch (e: GetCredentialException) {
                    viewModel.signInFailed()
                    return@launch
                }
                when (result) {
                    is GoogleSignInResult.IdToken -> viewModel.signIn(result.value, nonce)
                    GoogleSignInResult.NoAccount -> viewModel.signInFailed()
                    GoogleSignInResult.Canceled -> Unit
                }
            }
        },
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(stringResource(R.string.settings_account_android_sign_in))
    }
}
