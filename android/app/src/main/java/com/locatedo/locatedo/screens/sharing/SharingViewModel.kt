package com.locatedo.locatedo.screens.sharing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.PaywallRequests
import com.locatedo.locatedo.core.billing.PaywallTrigger
import com.locatedo.locatedo.core.data.MembershipRepository
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.sharing.HouseholdManager
import com.locatedo.locatedo.core.sharing.Invite
import com.locatedo.locatedo.core.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SharingStatus {
    OWNER_FREE,
    OWNER_ALONE,
    OWNER_SHARING,
    MEMBER,
}

data class SharingUiState(
    val isLoading: Boolean = true,
    val isSignedIn: Boolean = false,
    val isWorking: Boolean = false,
    val isRefreshing: Boolean = false,
    val memberships: List<Membership> = emptyList(),
    val currentUserId: UUID? = null,
    val isPro: Boolean = false,
    val hasFailed: Boolean = false,
) {
    val currentMembership: Membership?
        get() = memberships.firstOrNull { it.userId == currentUserId }

    val owner: Membership?
        get() = memberships.firstOrNull { it.role == MemberRole.OWNER }

    val isOwner: Boolean
        get() = currentMembership?.role == MemberRole.OWNER

    // Null until the first pull brings the device's own membership.
    val status: SharingStatus?
        get() = when {
            currentMembership == null -> null
            !isOwner -> SharingStatus.MEMBER
            !isPro -> SharingStatus.OWNER_FREE
            memberships.size > 1 -> SharingStatus.OWNER_SHARING
            else -> SharingStatus.OWNER_ALONE
        }

    val seatsLeft: Int
        get() = (MAX_MEMBERS - memberships.size).coerceAtLeast(0)

    val canAcceptInvite: Boolean
        get() = memberships.size <= 1

    val canLeave: Boolean
        get() = currentMembership != null && !isOwner

    fun canRemove(membership: Membership): Boolean = isOwner && membership.userId != currentUserId

    companion object {
        const val MAX_MEMBERS = 6
    }
}

sealed interface SharingEvent {
    data class InviteCreated(val invite: Invite) : SharingEvent
}

@HiltViewModel
class SharingViewModel @Inject constructor(
    membershipRepository: MembershipRepository,
    syncStateRepository: SyncStateRepository,
    authenticator: Authenticator,
    private val households: HouseholdManager,
    private val sync: SyncEngine,
    private val paywallRequests: PaywallRequests,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val hasFailed = MutableStateFlow(false)
    private val _events = MutableSharedFlow<SharingEvent>()

    val events: SharedFlow<SharingEvent> = _events

    val uiState: StateFlow<SharingUiState> = combine(
        membershipRepository.observeAll(),
        syncStateRepository.observe(),
        authenticator.session,
        households.isWorking,
        combine(isRefreshing, hasFailed) { refreshing, failed -> refreshing to failed },
    ) { memberships, syncState, session, working, (refreshing, failed) ->
        SharingUiState(
            isLoading = false,
            isSignedIn = session != null,
            isWorking = working,
            isRefreshing = refreshing,
            memberships = memberships,
            currentUserId = session?.userId,
            isPro = syncState.plan == Plan.PRO,
            hasFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SharingUiState())

    // The member list is only as fresh as the last pull.
    init {
        viewModelScope.launch {
            sync.sync()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                sync.sync()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun upgrade() = paywallRequests.request(PaywallTrigger.SHARE)

    fun createInvite() {
        viewModelScope.launch {
            hasFailed.value = false
            try {
                _events.emit(SharingEvent.InviteCreated(households.createInvite()))
            } catch (e: Exception) {
                Log.w(TAG, "Creating an invite failed", e)
                hasFailed.value = true
            }
        }
    }

    fun remove(userId: UUID) {
        viewModelScope.launch {
            hasFailed.value = false
            try {
                households.remove(userId)
            } catch (e: Exception) {
                Log.w(TAG, "Removing a member failed", e)
                hasFailed.value = true
            }
        }
    }

    fun leave() {
        viewModelScope.launch {
            hasFailed.value = false
            try {
                households.leave()
            } catch (e: Exception) {
                Log.w(TAG, "Leaving the household failed", e)
                hasFailed.value = true
            }
        }
    }

    private companion object {
        const val TAG = "Sharing"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
