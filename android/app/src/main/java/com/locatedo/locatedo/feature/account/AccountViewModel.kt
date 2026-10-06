package com.locatedo.locatedo.feature.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.account.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountFailure { SIGN_IN, DELETE }

data class AccountScreenState(
    val isSignedIn: Boolean = false,
    val isWorking: Boolean = false,
    val needsReplaceConfirmation: Boolean = false,
    val isConfirmingSignOut: Boolean = false,
    val hasUnsyncedWrites: Boolean = false,
    val failure: AccountFailure? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(private val accountManager: AccountManager) : ViewModel() {
    private data class Local(
        val isConfirmingSignOut: Boolean = false,
        val hasUnsyncedWrites: Boolean = false,
        val failure: AccountFailure? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<AccountScreenState> = combine(accountManager.uiState, local) { account, local ->
        AccountScreenState(
            isSignedIn = account.isSignedIn,
            isWorking = account.isWorking,
            needsReplaceConfirmation = account.needsReplaceConfirmation,
            isConfirmingSignOut = local.isConfirmingSignOut,
            hasUnsyncedWrites = local.hasUnsyncedWrites,
            failure = local.failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AccountScreenState())

    fun signIn(idToken: String, nonce: String) {
        local.update { it.copy(failure = null) }
        viewModelScope.launch {
            try {
                accountManager.signInWithGoogle(idToken, nonce)
            } catch (e: Exception) {
                Log.w(TAG, "Sign-in failed", e)
                local.update { it.copy(failure = AccountFailure.SIGN_IN) }
            }
        }
    }

    fun signInFailed() {
        local.update { it.copy(failure = AccountFailure.SIGN_IN) }
    }

    fun confirmReplacingLocalData() {
        viewModelScope.launch {
            try {
                accountManager.confirmReplacingLocalData()
            } catch (e: Exception) {
                Log.w(TAG, "Adopting the household failed", e)
                local.update { it.copy(failure = AccountFailure.SIGN_IN) }
            }
        }
    }

    fun cancelReplacingLocalData() = accountManager.cancelReplacingLocalData()

    fun prepareSignOut() {
        viewModelScope.launch {
            val unsynced = accountManager.hasUnsyncedWrites()
            local.update { it.copy(isConfirmingSignOut = true, hasUnsyncedWrites = unsynced) }
        }
    }

    fun dismissSignOut() = local.update { it.copy(isConfirmingSignOut = false) }

    fun signOut() {
        local.update { it.copy(isConfirmingSignOut = false) }
        viewModelScope.launch {
            try {
                accountManager.signOut()
            } catch (e: Exception) {
                Log.w(TAG, "Sign-out failed", e)
            }
        }
    }

    fun deleteAccount() {
        local.update { it.copy(failure = null) }
        viewModelScope.launch {
            try {
                accountManager.deleteAccount()
            } catch (e: Exception) {
                Log.w(TAG, "Account deletion failed", e)
                local.update { it.copy(failure = AccountFailure.DELETE) }
            }
        }
    }

    private companion object {
        const val TAG = "Account"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
