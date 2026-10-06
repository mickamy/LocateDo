package com.locatedo.locatedo.feature.sharing

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.testing.FakeHouseholdManager
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.util.Log, which the failure paths write to.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AcceptInviteViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val households = FakeHouseholdManager()
    private val memberships = FakeMembershipRepository()
    private val token = "invite-token-0123456789"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun anOpenedLinkFillsTheFieldUnlessSomethingWasTyped() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.start(token)

        assertEquals("https://locatedo.com/i/$token", viewModel.uiState.value.link)
        assertTrue(viewModel.uiState.value.canJoin)
        viewModel.setLink("typed")
        viewModel.start(token)
        assertEquals("typed", viewModel.uiState.value.link)
        assertFalse(viewModel.uiState.value.canJoin)
    }

    @Test
    fun joiningSendsTheTokenAndReportsSuccess() = runTest(dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        viewModel.setLink("https://locatedo.com/i/$token")

        viewModel.join()

        assertEquals(listOf(token), households.accepted)
        assertEquals(listOf(AcceptInviteEvent.Joined), events)
    }

    @Test
    fun somethingThatIsNotALinkIsRejectedBeforeTheServerIsAsked() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.setLink("https://example.com/i/$token")

        viewModel.join()

        assertEquals(InviteFailure.INVALID, viewModel.uiState.value.failure)
        assertTrue(households.accepted.isEmpty())
    }

    @Test
    fun sharingWithOthersMeansLeavingFirst() = runTest(dispatcher) {
        memberships.state.value = listOf(
            Membership(testSession.userId, MemberRole.OWNER, "Taro", now, now),
            Membership(UUID.randomUUID(), MemberRole.MEMBER, "Hanako", now, now),
        )
        val viewModel = viewModel()
        viewModel.setLink(token)

        viewModel.join()

        assertEquals(InviteFailure.LEAVE_FIRST, viewModel.uiState.value.failure)
        assertTrue(households.accepted.isEmpty())
    }

    @Test
    fun serverRejectionsAreExplained() = runTest(dispatcher) {
        for ((code, expected) in listOf(
            Code.FAILED_PRECONDITION to InviteFailure.UNAVAILABLE,
            Code.NOT_FOUND to InviteFailure.UNAVAILABLE,
            Code.ALREADY_EXISTS to InviteFailure.ALREADY_MEMBER,
            Code.UNAVAILABLE to InviteFailure.FAILED,
        )) {
            val viewModel = viewModel()
            households.failure = ConnectException(code)
            viewModel.setLink(token)

            viewModel.join()

            assertEquals(code.name, expected, viewModel.uiState.value.failure)
        }
    }

    private fun TestScope.viewModel(): AcceptInviteViewModel {
        val viewModel = AcceptInviteViewModel(households, memberships, fakeAuthenticator(testSession))
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun TestScope.events(viewModel: AcceptInviteViewModel): List<AcceptInviteEvent> {
        val events = mutableListOf<AcceptInviteEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        return events
    }
}
