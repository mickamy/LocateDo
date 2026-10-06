package com.locatedo.locatedo.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.locatedo.locatedo.BuildConfig
import java.security.SecureRandom

object Nonce {
    private val random = SecureRandom()

    fun make(byteCount: Int = 32): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

sealed interface GoogleSignInResult {
    data class IdToken(val value: String) : GoogleSignInResult
    data object Canceled : GoogleSignInResult
    data object NoAccount : GoogleSignInResult
}

// Credential Manager's Sign in with Google: the id token names the web client as its audience, and carries the nonce
// as given, which is what the server checks.
class GoogleSignIn {
    suspend fun signIn(context: Context, nonce: String): GoogleSignInResult {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            return GoogleSignInResult.Canceled
        } catch (e: NoCredentialException) {
            return GoogleSignInResult.NoAccount
        }
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "unexpected credential type ${credential.type}"
        }
        return GoogleSignInResult.IdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    }
}
