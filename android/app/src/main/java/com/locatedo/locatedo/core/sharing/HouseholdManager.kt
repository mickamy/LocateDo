package com.locatedo.locatedo.core.sharing

import android.util.Log
import com.locatedo.household.v1.HouseholdServiceClientInterface
import com.locatedo.household.v1.acceptInviteRequest
import com.locatedo.household.v1.createInviteRequest
import com.locatedo.household.v1.removeMemberRequest
import com.locatedo.locatedo.core.account.AccountManager
import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.SignedOutException
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.sync.ProtoInput
import com.locatedo.locatedo.core.sync.SyncEngine
import com.locatedo.locatedo.core.sync.toInstant
import com.locatedo.locatedo.core.sync.toModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Invite(val url: String, val expiresAt: Instant)

interface HouseholdManager {
    val isWorking: StateFlow<Boolean>

    suspend fun remove(userId: UUID)

    suspend fun leave()

    suspend fun createInvite(): Invite

    suspend fun accept(token: String)

    suspend fun handleRemoval()
}

// Moving between households: inviting, joining, leaving, removing, and being removed.
@Singleton
class DefaultHouseholdManager @Inject constructor(
    private val household: HouseholdServiceClientInterface,
    private val authenticator: Authenticator,
    private val syncState: SyncStateRepository,
    private val account: AccountManager,
    private val sync: SyncEngine,
    private val preferences: AppPreferences,
    private val analytics: Analytics,
) : HouseholdManager {
    private val _isWorking = MutableStateFlow(false)

    override val isWorking: StateFlow<Boolean> = _isWorking

    override suspend fun remove(userId: UUID) = working {
        removeMember(userId)
        sync.sync()
    }

    // Queued writes go up first so nothing is lost; once the server has let the member out, the device starts over.
    override suspend fun leave() = working {
        val userId = authenticator.current()?.userId ?: throw SignedOutException()
        sync.drain()
        removeMember(userId)
        account.startOver()
    }

    override suspend fun createInvite(): Invite = working {
        val householdId = syncState.get().householdId ?: throw SignedOutException()
        val response = authenticator.authorized {
            household.createInvite(createInviteRequest { this.householdId = ProtoInput.id(householdId) })
        }
        Invite(InviteLink.url(response.token), response.expiresAt.toInstant())
    }

    override suspend fun accept(token: String) = working {
        sync.drain()
        val response = authenticator.authorized { household.acceptInvite(acceptInviteRequest { this.token = token }) }
        val householdId = runCatching { UUID.fromString(response.household.id) }.getOrNull()
        checkNotNull(householdId) { "AcceptInvite returned an invalid household id" }
        account.join(householdId, response.household.plan.toModel())
        analytics.log(AnalyticsEvent.INVITE_ACCEPTED)
    }

    override suspend fun handleRemoval() {
        try {
            account.startOver()
        } catch (e: Exception) {
            Log.w(TAG, "Could not start over after being removed from the household", e)
        }
        preferences.setPendingRemovedNotice(true)
    }

    private suspend fun removeMember(userId: UUID) {
        val householdId = syncState.get().householdId ?: return
        authenticator.authorized {
            household.removeMember(
                removeMemberRequest {
                    this.householdId = ProtoInput.id(householdId)
                    this.userId = ProtoInput.id(userId)
                },
            )
        }
    }

    private suspend fun <T> working(block: suspend () -> T): T {
        _isWorking.value = true
        try {
            return block()
        } finally {
            _isWorking.value = false
        }
    }

    private companion object {
        const val TAG = "Sharing"
    }
}
