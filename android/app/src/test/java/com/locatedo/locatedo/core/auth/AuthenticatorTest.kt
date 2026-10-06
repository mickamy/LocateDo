package com.locatedo.locatedo.core.auth

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.locatedo.locatedo.core.api.AccessTokenStore
import com.locatedo.locatedo.testing.FakeAccountService
import com.locatedo.locatedo.testing.InMemorySessionStore
import com.locatedo.locatedo.testing.failure
import com.locatedo.locatedo.testing.success
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticatorTest {
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val account = FakeAccountService()
    private val tokens = AccessTokenStore()
    private val userId = UUID.fromString(FakeAccountService.USER_ID)

    @Test
    fun restoresTheStoredSessionAndPublishesItsToken() = runTest {
        val store = InMemorySessionStore(session(expiresIn = Duration.ofHours(1)))
        val authenticator = authenticator(store)

        val current = authenticator.current()

        assertEquals(store.session, current)
        assertEquals("access", tokens.current)
    }

    @Test
    fun refreshesWhenTheAccessTokenIsAboutToExpire() = runTest {
        val store = InMemorySessionStore(session(expiresIn = Duration.ofSeconds(30)))
        val authenticator = authenticator(store)

        authenticator.refreshIfNeeded()

        assertEquals(listOf("refresh"), account.refreshes)
        assertEquals("access-1", authenticator.current()?.accessToken)
        assertEquals("access-1", tokens.current)
        assertEquals("refresh-1", store.session?.refreshToken)
    }

    @Test
    fun leavesAFreshTokenAlone() = runTest {
        val authenticator = authenticator(InMemorySessionStore(session(expiresIn = Duration.ofMinutes(10))))

        authenticator.refreshIfNeeded()

        assertTrue(account.refreshes.isEmpty())
        assertEquals("access", tokens.current)
    }

    @Test
    fun authorizedRetriesOnceAfterAnUnauthenticatedResponse() = runTest {
        val authenticator = authenticator(InMemorySessionStore(session(expiresIn = Duration.ofHours(1))))
        var calls = 0

        val result = authenticator.authorized {
            calls += 1
            if (calls == 1) failure(Code.UNAUTHENTICATED) else success("ok")
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
        assertEquals(1, account.refreshes.size)
    }

    @Test
    fun authorizedPassesOtherFailuresThrough() = runTest {
        val authenticator = authenticator(InMemorySessionStore(session(expiresIn = Duration.ofHours(1))))

        try {
            authenticator.authorized<String> { failure(Code.UNAVAILABLE) }
            fail("expected a ConnectException")
        } catch (e: ConnectException) {
            assertEquals(Code.UNAVAILABLE, e.code)
        }
        assertTrue(account.refreshes.isEmpty())
    }

    @Test
    fun aRejectedRefreshEndsTheSession() = runTest {
        val store = InMemorySessionStore(session(expiresIn = Duration.ofSeconds(30)))
        val authenticator = authenticator(store)
        var ended = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { authenticator.sessionEnded.collect { ended += 1 } }
        account.refreshFailure = Code.UNAUTHENTICATED

        try {
            authenticator.refreshIfNeeded()
            fail("expected a ConnectException")
        } catch (e: ConnectException) {
            assertEquals(Code.UNAUTHENTICATED, e.code)
        }

        assertNull(authenticator.current())
        assertNull(store.session)
        assertNull(tokens.current)
        assertEquals(1, ended)
    }

    @Test
    fun aTransientRefreshFailureKeepsTheSession() = runTest {
        val store = InMemorySessionStore(session(expiresIn = Duration.ofSeconds(30)))
        val authenticator = authenticator(store)
        account.refreshFailure = Code.UNAVAILABLE

        try {
            authenticator.refresh()
            fail("expected a ConnectException")
        } catch (e: ConnectException) {
            assertEquals(Code.UNAVAILABLE, e.code)
        }

        assertEquals("access", authenticator.current()?.accessToken)
    }

    @Test
    fun concurrentRefreshesShareOneCall() = runTest {
        val authenticator = authenticator(InMemorySessionStore(session(expiresIn = Duration.ofSeconds(30))))
        account.refreshDelayMillis = 100

        launch { authenticator.refresh() }
        launch { authenticator.refresh() }
        advanceUntilIdle()

        assertEquals(1, account.refreshes.size)
        assertEquals("access-1", authenticator.current()?.accessToken)
    }

    @Test
    fun refreshingWhileSignedOutFails() = runTest {
        val authenticator = authenticator(InMemorySessionStore())

        try {
            authenticator.refresh()
            fail("expected SignedOutException")
        } catch (e: SignedOutException) {
            assertTrue(account.refreshes.isEmpty())
        }
    }

    private fun TestScope.authenticator(store: InMemorySessionStore) = Authenticator(store, account, tokens, clock, this)

    private fun session(expiresIn: Duration) = Session(userId, "access", now.plus(expiresIn), "refresh")
}
