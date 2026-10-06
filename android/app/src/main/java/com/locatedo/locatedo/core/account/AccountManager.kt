package com.locatedo.locatedo.core.account

import android.util.Log
import com.connectrpc.ResponseMessage
import com.locatedo.account.v1.AccountServiceClientInterface
import com.locatedo.account.v1.SignOutRequestKt
import com.locatedo.account.v1.deleteAccountRequest
import com.locatedo.account.v1.signInWithGoogleRequest
import com.locatedo.account.v1.signOutRequest
import com.locatedo.device.v1.Platform
import com.locatedo.household.v1.HouseholdServiceClientInterface
import com.locatedo.locatedo.core.api.getOrThrow
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.billing.Entitlements
import com.locatedo.locatedo.core.auth.InvalidSessionException
import com.locatedo.locatedo.core.auth.Session
import com.locatedo.locatedo.core.common.uuidV7
import com.locatedo.locatedo.core.data.CategoryRepository
import com.locatedo.locatedo.core.data.LocalData
import com.locatedo.locatedo.core.data.PlaceRepository
import com.locatedo.locatedo.core.data.SyncStateRepository
import com.locatedo.locatedo.core.data.TodoRepository
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.core.push.DeviceRegistration
import com.locatedo.locatedo.core.sync.InitialUpload
import com.locatedo.locatedo.core.sync.WriteQueue
import com.locatedo.locatedo.core.sync.toModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class AccountUiState(
    val isSignedIn: Boolean = false,
    val isWorking: Boolean = false,
    val needsReplaceConfirmation: Boolean = false,
)

// Sign-in, the household that comes with it, and the ways out: sign-out, deletion, and a session the server ended.
@Singleton
class AccountManager @Inject constructor(
    private val account: AccountServiceClientInterface,
    private val household: HouseholdServiceClientInterface,
    private val authenticator: Authenticator,
    private val placeRepository: PlaceRepository,
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository,
    private val syncState: SyncStateRepository,
    private val localData: LocalData,
    private val queue: WriteQueue,
    private val deviceRegistration: DeviceRegistration,
    private val entitlements: Entitlements,
    private val preferences: AppPreferences,
    private val clock: Clock,
) {
    private data class PendingAdoption(val session: Session, val householdId: UUID)

    private val isWorking = MutableStateFlow(false)
    private val pendingAdoption = MutableStateFlow<PendingAdoption?>(null)
    private val _householdReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var pendingHouseholdId: UUID? = null

    val uiState: Flow<AccountUiState> = combine(authenticator.session, isWorking, pendingAdoption) { session, working, adoption ->
        AccountUiState(isSignedIn = session != null, isWorking = working, needsReplaceConfirmation = adoption != null)
    }

    // The device has a household to sync with: created, adopted at sign-in, or joined through an invite.
    val householdReady: SharedFlow<Unit> = _householdReady

    suspend fun isSignedIn(): Boolean = authenticator.current() != null

    // A second device, or a reinstall, finds its household on the server; local data is replaced, after a
    // confirmation when the user built any.
    suspend fun signInWithGoogle(idToken: String, nonce: String) = working {
        val response = account.signInWithGoogle(
            signInWithGoogleRequest {
                this.idToken = idToken
                this.nonce = nonce
            },
        ).getOrThrow()
        val session = Session.fromProto(response.session) ?: throw InvalidSessionException()
        val householdId = if (response.hasHouseholdId()) runCatching { UUID.fromString(response.householdId) }.getOrNull() else null
        if (householdId != null) {
            val adoption = PendingAdoption(session, householdId)
            if (localData.hasUserData()) {
                pendingAdoption.value = adoption
            } else {
                adopt(adoption)
            }
            return@working
        }
        authenticator.signIn(session)
        uploadLocalDataIfNeeded()
    }

    suspend fun confirmReplacingLocalData() {
        val adoption = pendingAdoption.value ?: return
        working {
            pendingAdoption.value = null
            adopt(adoption)
        }
    }

    fun cancelReplacingLocalData() {
        pendingAdoption.value = null
    }

    // The household id is kept across a failed attempt so a retry is idempotent on the server.
    suspend fun uploadLocalDataIfNeeded() {
        if (!isSignedIn() || syncState.get().householdId != null) {
            return
        }
        val householdId = pendingHouseholdId ?: uuidV7(clock.instant()).also { pendingHouseholdId = it }
        val request = InitialUpload.request(
            householdId = householdId,
            categories = categoryRepository.observeAll().first(),
            places = placeRepository.observeAll().first(),
            todos = todoRepository.observeAll().first(),
        )
        val response = authenticator.authorized { household.createHousehold(request) }
        val created = runCatching { UUID.fromString(response.household.id) }.getOrNull() ?: householdId
        syncState.set(SyncState(householdId = created, cursor = response.cursor, plan = response.household.plan.toModel()))
        pendingHouseholdId = null
        householdReady()
    }

    suspend fun hasUnsyncedWrites(): Boolean = !queue.isEmpty()

    suspend fun signOut() = working {
        authenticator.current()?.let { session ->
            val request = signOutRequest {
                refreshToken = session.refreshToken
                deviceRegistration.installationId.value?.let { id ->
                    device = SignOutRequestKt.device {
                        platform = Platform.PLATFORM_ANDROID
                        pushToken = id
                    }
                }
            }
            val response = account.signOut(request)
            if (response is ResponseMessage.Failure) {
                Log.w(TAG, "Server sign-out failed; signing out locally", response.cause)
            }
        }
        authenticator.signOut()
        pendingAdoption.value = null
        localData.reset()
        entitlements.logOut()
    }

    suspend fun deleteAccount() = working {
        authenticator.authorized { account.deleteAccount(deleteAccountRequest {}) }
        authenticator.signOut()
        pendingAdoption.value = null
        localData.reset()
        preferences.reset()
        entitlements.logOut()
    }

    suspend fun startOver() {
        pendingAdoption.value = null
        localData.reset()
        uploadLocalDataIfNeeded()
    }

    suspend fun endSession() {
        pendingAdoption.value = null
        localData.reset()
        preferences.setPendingSessionEndedNotice(true)
        entitlements.logOut()
    }

    suspend fun join(householdId: UUID, plan: Plan) {
        localData.deleteSynced()
        syncState.set(SyncState(householdId = householdId, cursor = 0, plan = plan))
        householdReady()
    }

    private suspend fun adopt(adoption: PendingAdoption) {
        authenticator.signIn(adoption.session)
        join(adoption.householdId, Plan.FREE)
    }

    // The store learns the user id here, so a subscription bought before signing in follows the account.
    private suspend fun householdReady() {
        _householdReady.tryEmit(Unit)
        deviceRegistration.registerIfSignedIn()
        authenticator.current()?.let { entitlements.logIn(it.userId) }
    }

    private suspend fun <T> working(block: suspend () -> T): T {
        isWorking.value = true
        try {
            return block()
        } finally {
            isWorking.value = false
        }
    }

    private companion object {
        const val TAG = "Account"
    }
}
