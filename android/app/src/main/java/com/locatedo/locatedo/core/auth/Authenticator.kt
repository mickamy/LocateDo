package com.locatedo.locatedo.core.auth

import com.connectrpc.Code
import com.connectrpc.ResponseMessage
import com.locatedo.account.v1.AccountServiceClientInterface
import com.locatedo.account.v1.refreshTokenRequest
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.core.api.getOrThrow
import com.locatedo.locatedo.core.common.di.ApplicationScope
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Holds the session, refreshes the access token ahead of its expiry, and retries a call once when the server
// still rejects the token. A refresh the server refuses ends the session.
@Singleton
class Authenticator @Inject constructor(
    private val store: SessionStore,
    private val account: AccountServiceClientInterface,
    private val tokens: AccessTokenStore,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _session = MutableStateFlow<Session?>(null)
    private val _sessionEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val loaded: Deferred<Unit> = scope.async { restore() }
    private val refreshing = Mutex()

    val session: StateFlow<Session?> = _session

    val sessionEnded: SharedFlow<Unit> = _sessionEnded

    suspend fun current(): Session? {
        loaded.await()
        return _session.value
    }

    suspend fun signIn(session: Session) {
        loaded.await()
        store.save(session)
        _session.value = session
        tokens.current = session.accessToken
    }

    suspend fun signOut() {
        loaded.await()
        store.clear()
        _session.value = null
        tokens.current = null
    }

    suspend fun refreshIfNeeded() {
        val session = current() ?: return
        if (session.isExpiring(clock.instant(), REFRESH_LEEWAY)) {
            refresh(session)
        }
    }

    suspend fun refresh() {
        refresh(current() ?: throw SignedOutException())
    }

    suspend fun <T> authorized(call: suspend () -> ResponseMessage<T>): T {
        refreshIfNeeded()
        val first = call()
        if (first is ResponseMessage.Failure && first.cause.code == Code.UNAUTHENTICATED) {
            refresh()
            return call().getOrThrow()
        }
        return first.getOrThrow()
    }

    // Concurrent callers queue on the lock; whoever arrives after a refresh finds a newer session and leaves.
    private suspend fun refresh(using: Session) {
        refreshing.withLock {
            val current = _session.value ?: throw SignedOutException()
            if (current != using) {
                return
            }
            val response = account.refreshToken(refreshTokenRequest { refreshToken = using.refreshToken })
            if (response is ResponseMessage.Failure) {
                val code = response.cause.code
                if (code == Code.UNAUTHENTICATED || code == Code.PERMISSION_DENIED) {
                    signOut()
                    _sessionEnded.tryEmit(Unit)
                }
                throw response.cause
            }
            val refreshed = Session.fromProto(response.getOrThrow().session) ?: throw InvalidSessionException()
            signIn(refreshed)
        }
    }

    private suspend fun restore() {
        val session = store.load()
        _session.value = session
        tokens.current = session?.accessToken
    }

    companion object {
        val REFRESH_LEEWAY: Duration = Duration.ofSeconds(60)
    }
}
