package com.locatedo.locatedo.feature.sharing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.sharing.HouseholdManager
import com.locatedo.locatedo.core.sharing.InviteLink
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class InviteFailure {
    INVALID,
    UNAVAILABLE,
    ALREADY_MEMBER,
    LEAVE_FIRST,
    FAILED,
}

data class AcceptInviteUiState(
    val link: String = "",
    val isSignedIn: Boolean = false,
    val isWorking: Boolean = false,
    val failure: InviteFailure? = null,
) {
    val canJoin: Boolean
        get() = InviteLink.token(link) != null && !isWorking
}

sealed interface AcceptInviteEvent {
    data object Joined : AcceptInviteEvent
}

@HiltViewModel
class AcceptInviteViewModel @Inject constructor(
    private val households: HouseholdManager,
    membershipRepository: MembershipRepository,
    authenticator: Authenticator,
) : ViewModel() {
    private val link = MutableStateFlow("")
    private val failure = MutableStateFlow<InviteFailure?>(null)
    private val memberCount = membershipRepository.observeAll().map { it.size }
    private val _events = MutableSharedFlow<AcceptInviteEvent>()

    val events: SharedFlow<AcceptInviteEvent> = _events

    val uiState: StateFlow<AcceptInviteUiState> = combine(
        link,
        authenticator.session,
        households.isWorking,
        failure,
    ) { link, session, working, failure ->
        AcceptInviteUiState(link = link, isSignedIn = session != null, isWorking = working, failure = failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AcceptInviteUiState())

    // A token from an opened link fills the field; typing afterwards wins.
    fun start(token: String?) {
        if (token != null && link.value.isEmpty()) {
            link.value = InviteLink.url(token)
        }
    }

    fun setLink(text: String) {
        link.value = text
        failure.value = null
    }

    // Someone already sharing with others has to leave first: the server would merge their household otherwise.
    fun join() {
        viewModelScope.launch {
            failure.value = null
            val token = InviteLink.token(link.value)
            if (token == null) {
                failure.value = InviteFailure.INVALID
                return@launch
            }
            if (memberCount.first() > 1) {
                failure.value = InviteFailure.LEAVE_FIRST
                return@launch
            }
            try {
                households.accept(token)
                _events.emit(AcceptInviteEvent.Joined)
            } catch (e: ConnectException) {
                Log.w(TAG, "Accepting an invite failed", e)
                failure.value = failureFor(e.code)
            } catch (e: Exception) {
                Log.w(TAG, "Accepting an invite failed", e)
                failure.value = InviteFailure.FAILED
            }
        }
    }

    companion object {
        private const val TAG = "Sharing"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun failureFor(code: Code): InviteFailure = when (code) {
            Code.FAILED_PRECONDITION, Code.NOT_FOUND -> InviteFailure.UNAVAILABLE
            Code.ALREADY_EXISTS -> InviteFailure.ALREADY_MEMBER
            else -> InviteFailure.FAILED
        }
    }
}
