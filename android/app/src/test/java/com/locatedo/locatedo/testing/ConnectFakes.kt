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
import com.locatedo.account.v1.refreshTokenResponse
import com.locatedo.account.v1.session
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
import com.locatedo.household.v1.household
import com.locatedo.locatedo.core.auth.Session
import com.locatedo.locatedo.core.auth.SessionStore
import com.locatedo.locatedo.core.push.PushTokenSource
import com.locatedo.locatedo.core.sync.toTimestamp
import java.time.Instant
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

    companion object {
        const val USER_ID = "0199bd00-0000-7000-8000-000000000001"
    }
}

class FakeHouseholdService : HouseholdServiceClientInterface {
    var lastCreate: CreateHouseholdRequest? = null
    var createCalls = 0
    var failNextCreate = false

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
        failure(Code.UNIMPLEMENTED)

    override suspend fun acceptInvite(request: AcceptInviteRequest, headers: Headers): ResponseMessage<AcceptInviteResponse> =
        failure(Code.UNIMPLEMENTED)

    override suspend fun removeMember(request: RemoveMemberRequest, headers: Headers): ResponseMessage<RemoveMemberResponse> =
        failure(Code.UNIMPLEMENTED)
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

class InMemorySessionStore(var session: Session? = null) : SessionStore {
    override suspend fun load(): Session? = session

    override suspend fun save(session: Session) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

class FakePushTokenSource(var token: String? = null) : PushTokenSource {
    override suspend fun token(): String? = token
}
