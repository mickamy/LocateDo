package com.locatedo.locatedo.testing

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.connectrpc.Headers
import com.connectrpc.ResponseMessage
import com.locatedo.account.v1.AccountServiceClientInterface
import com.locatedo.account.v1.DeleteAccountRequest
import com.locatedo.account.v1.DeleteAccountResponse
import com.locatedo.account.v1.RefreshTokenRequest
import com.locatedo.account.v1.RefreshTokenResponse
import com.locatedo.account.v1.SignInWithAppleRequest
import com.locatedo.account.v1.SignInWithAppleResponse
import com.locatedo.account.v1.SignInWithGoogleRequest
import com.locatedo.account.v1.SignInWithGoogleResponse
import com.locatedo.account.v1.SignOutRequest
import com.locatedo.account.v1.SignOutResponse
import com.locatedo.account.v1.SyncEntitlementRequest
import com.locatedo.account.v1.SyncEntitlementResponse
import com.locatedo.account.v1.refreshTokenResponse
import com.locatedo.account.v1.session
import com.locatedo.category.v1.CategoryServiceClientInterface
import com.locatedo.category.v1.DeleteCategoryRequest
import com.locatedo.category.v1.DeleteCategoryResponse
import com.locatedo.category.v1.PutCategoryRequest
import com.locatedo.category.v1.PutCategoryResponse
import com.locatedo.device.v1.DeviceServiceClientInterface
import com.locatedo.device.v1.RegisterDeviceRequest
import com.locatedo.device.v1.RegisterDeviceResponse
import com.locatedo.household.v1.AcceptInviteRequest
import com.locatedo.household.v1.AcceptInviteResponse
import com.locatedo.household.v1.CreateHouseholdRequest
import com.locatedo.household.v1.CreateHouseholdResponse
import com.locatedo.household.v1.CreateInviteRequest
import com.locatedo.household.v1.CreateInviteResponse
import com.locatedo.household.v1.HouseholdServiceClientInterface
import com.locatedo.household.v1.Plan
import com.locatedo.household.v1.RemoveMemberRequest
import com.locatedo.household.v1.RemoveMemberResponse
import com.locatedo.household.v1.createHouseholdResponse
import com.locatedo.household.v1.createInviteResponse
import com.locatedo.household.v1.household
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.auth.Authenticator
import com.locatedo.locatedo.core.auth.Session
import com.locatedo.locatedo.core.auth.SessionStore
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.push.InstallationIdSource
import com.locatedo.locatedo.core.sync.toTimestamp
import com.locatedo.place.v1.DeletePlaceRequest
import com.locatedo.place.v1.DeletePlaceResponse
import com.locatedo.place.v1.PlaceServiceClientInterface
import com.locatedo.place.v1.PutPlaceRequest
import com.locatedo.place.v1.PutPlaceResponse
import com.locatedo.sync.v1.PullRequest
import com.locatedo.sync.v1.PullResponse
import com.locatedo.sync.v1.SyncServiceClientInterface
import com.locatedo.sync.v1.pullResponse
import com.locatedo.todo.v1.DeleteTodoRequest
import com.locatedo.todo.v1.DeleteTodoResponse
import com.locatedo.todo.v1.PutTodoRequest
import com.locatedo.todo.v1.PutTodoResponse
import com.locatedo.todo.v1.SetTodoCompletionRequest
import com.locatedo.todo.v1.SetTodoCompletionResponse
import com.locatedo.todo.v1.TodoServiceClientInterface
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

fun <T> success(message: T): ResponseMessage<T> = ResponseMessage.Success(message, emptyMap(), emptyMap())

fun <T> failure(code: Code): ResponseMessage<T> = ResponseMessage.Failure(ConnectException(code), emptyMap(), emptyMap())

fun sessionProto(userId: String, accessToken: String, refreshToken: String, expiresAt: Instant): com.locatedo.account.v1.Session =
    session {
        this.userId = userId
        this.accessToken = accessToken
        accessTokenExpiresAt = expiresAt.toTimestamp()
        this.refreshToken = refreshToken
    }

class FakeAccountService : AccountServiceClientInterface {
    var signInResponse: ResponseMessage<SignInWithGoogleResponse> = failure(Code.UNIMPLEMENTED)
    val signIns = mutableListOf<SignInWithGoogleRequest>()
    val refreshes = mutableListOf<String>()
    var refreshFailure: Code? = null
    var refreshDelayMillis = 0L
    var refreshedExpiresAt: Instant = Instant.parse("2026-10-06T01:00:00Z")
    val signOuts = mutableListOf<SignOutRequest>()
    var signOutFailure: Code? = null
    var deleteCalls = 0
    var syncEntitlementCalls = 0

    override suspend fun signInWithApple(request: SignInWithAppleRequest, headers: Headers): ResponseMessage<SignInWithAppleResponse> =
        failure(Code.UNIMPLEMENTED)

    override suspend fun signInWithGoogle(request: SignInWithGoogleRequest, headers: Headers): ResponseMessage<SignInWithGoogleResponse> {
        signIns += request
        return signInResponse
    }

    override suspend fun refreshToken(request: RefreshTokenRequest, headers: Headers): ResponseMessage<RefreshTokenResponse> {
        refreshes += request.refreshToken
        if (refreshDelayMillis > 0) {
            delay(refreshDelayMillis)
        }
        refreshFailure?.let { return failure(it) }
        val count = refreshes.size
        return success(
            refreshTokenResponse {
                session = sessionProto(USER_ID, "access-$count", "refresh-$count", refreshedExpiresAt)
            },
        )
    }

    override suspend fun signOut(request: SignOutRequest, headers: Headers): ResponseMessage<SignOutResponse> {
        signOuts += request
        signOutFailure?.let { return failure(it) }
        return success(SignOutResponse.getDefaultInstance())
    }

    override suspend fun deleteAccount(request: DeleteAccountRequest, headers: Headers): ResponseMessage<DeleteAccountResponse> {
        deleteCalls += 1
        return success(DeleteAccountResponse.getDefaultInstance())
    }

    override suspend fun syncEntitlement(request: SyncEntitlementRequest, headers: Headers): ResponseMessage<SyncEntitlementResponse> {
        syncEntitlementCalls += 1
        return success(SyncEntitlementResponse.getDefaultInstance())
    }

    companion object {
        const val USER_ID = "0199bd00-0000-7000-8000-000000000001"
    }
}

class FakeHouseholdService : HouseholdServiceClientInterface {
    var lastCreate: CreateHouseholdRequest? = null
    var createCalls = 0
    var failNextCreate = false
    var inviteToken = "invite-token-0123456789"
    var inviteExpiresAt: Instant = Instant.ofEpochSecond(1_800_000_000)
    val acceptedTokens = mutableListOf<String>()
    var acceptResponse: ResponseMessage<AcceptInviteResponse> = failure(Code.UNIMPLEMENTED)
    val removed = mutableListOf<RemoveMemberRequest>()
    var failRemove = false

    override suspend fun createHousehold(request: CreateHouseholdRequest, headers: Headers): ResponseMessage<CreateHouseholdResponse> {
        lastCreate = request
        createCalls += 1
        if (failNextCreate) {
            failNextCreate = false
            return failure(Code.UNAVAILABLE)
        }
        return success(
            createHouseholdResponse {
                household = household {
                    id = request.id
                    plan = Plan.PLAN_FREE
                }
                cursor = 42
            },
        )
    }

    override suspend fun createInvite(request: CreateInviteRequest, headers: Headers): ResponseMessage<CreateInviteResponse> =
        success(
            createInviteResponse {
                token = inviteToken
                expiresAt = inviteExpiresAt.toTimestamp()
            },
        )

    override suspend fun acceptInvite(request: AcceptInviteRequest, headers: Headers): ResponseMessage<AcceptInviteResponse> {
        acceptedTokens += request.token
        return acceptResponse
    }

    override suspend fun removeMember(request: RemoveMemberRequest, headers: Headers): ResponseMessage<RemoveMemberResponse> {
        removed += request
        if (failRemove) {
            return failure(Code.UNAVAILABLE)
        }
        return success(RemoveMemberResponse.getDefaultInstance())
    }
}

class FakeDeviceService : DeviceServiceClientInterface {
    val registered = mutableListOf<RegisterDeviceRequest>()
    var failure: Code? = null

    override suspend fun registerDevice(request: RegisterDeviceRequest, headers: Headers): ResponseMessage<RegisterDeviceResponse> {
        registered += request
        failure?.let { return failure(it) }
        return success(RegisterDeviceResponse.getDefaultInstance())
    }
}

val testSession = Session(UUID.fromString(FakeAccountService.USER_ID), "access", fixedNow.plusSeconds(3600), "refresh")

// Restores on an unconfined scope, so the signed-in or signed-out state is ready without a test scheduler.
fun fakeAuthenticator(session: Session? = null, account: AccountServiceClientInterface = FakeAccountService()): Authenticator =
    Authenticator(InMemorySessionStore(session), account, AccessTokenStore(), fixedClock, CoroutineScope(Dispatchers.Unconfined))

class FakeWriteServices : PlaceServiceClientInterface, TodoServiceClientInterface, CategoryServiceClientInterface {
    val sent = mutableListOf<String>()
    val householdIds = mutableListOf<String>()
    private val failures = ArrayDeque<Code>()

    // Each code answers one call, in order; later calls succeed.
    fun fail(vararg codes: Code) {
        failures.clear()
        failures.addAll(codes)
    }

    override suspend fun putPlace(request: PutPlaceRequest, headers: Headers): ResponseMessage<PutPlaceResponse> =
        respond("putPlace", request.householdId, PutPlaceResponse.getDefaultInstance())

    override suspend fun deletePlace(request: DeletePlaceRequest, headers: Headers): ResponseMessage<DeletePlaceResponse> =
        respond("deletePlace", null, DeletePlaceResponse.getDefaultInstance())

    override suspend fun putTodo(request: PutTodoRequest, headers: Headers): ResponseMessage<PutTodoResponse> =
        respond("putTodo", request.householdId, PutTodoResponse.getDefaultInstance())

    override suspend fun setTodoCompletion(request: SetTodoCompletionRequest, headers: Headers): ResponseMessage<SetTodoCompletionResponse> =
        respond("setTodoCompletion", null, SetTodoCompletionResponse.getDefaultInstance())

    override suspend fun deleteTodo(request: DeleteTodoRequest, headers: Headers): ResponseMessage<DeleteTodoResponse> =
        respond("deleteTodo", null, DeleteTodoResponse.getDefaultInstance())

    override suspend fun putCategory(request: PutCategoryRequest, headers: Headers): ResponseMessage<PutCategoryResponse> =
        respond("putCategory", request.householdId, PutCategoryResponse.getDefaultInstance())

    override suspend fun deleteCategory(request: DeleteCategoryRequest, headers: Headers): ResponseMessage<DeleteCategoryResponse> =
        respond("deleteCategory", null, DeleteCategoryResponse.getDefaultInstance())

    private fun <T> respond(name: String, householdId: String?, message: T): ResponseMessage<T> {
        sent += name
        if (householdId != null) {
            householdIds += householdId
        }
        val code = failures.removeFirstOrNull() ?: return success(message)
        return failure(code)
    }
}

class FakeSyncService : SyncServiceClientInterface {
    val cursors = mutableListOf<Long>()
    val householdIds = mutableListOf<String>()
    private val pages = ArrayDeque<PullResponse>()
    private var failAfterPages: Int? = null
    private var failureCode = Code.UNAVAILABLE

    fun respond(vararg responses: PullResponse) {
        pages.clear()
        pages.addAll(responses)
    }

    fun fail(afterPages: Int, code: Code = Code.UNAVAILABLE) {
        failAfterPages = afterPages
        failureCode = code
    }

    // Once the pages run out, an empty page echoes the cursor back.
    override suspend fun pull(request: PullRequest, headers: Headers): ResponseMessage<PullResponse> {
        cursors += request.cursor
        householdIds += request.householdId
        val failAfter = failAfterPages
        if (failAfter != null && cursors.size > failAfter) {
            return failure(failureCode)
        }
        return success(pages.removeFirstOrNull() ?: pullResponse { cursor = request.cursor })
    }
}

class InMemorySessionStore(var session: Session? = null) : SessionStore {
    override suspend fun load(): Session? = session

    override suspend fun save(session: Session) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

class FakeInstallationIdSource(var installationId: String? = null) : InstallationIdSource {
    override suspend fun installationId(): String? = installationId
}
