package com.locatedo.locatedo.feature.sharing

import com.locatedo.locatedo.core.model.MemberRole
import com.locatedo.locatedo.core.model.Membership
import com.locatedo.locatedo.core.model.Plan
import com.locatedo.locatedo.core.model.SyncState
import com.locatedo.locatedo.testing.FakeHouseholdManager
import com.locatedo.locatedo.testing.FakeMembershipRepository
import com.locatedo.locatedo.testing.FakeSyncEngine
import com.locatedo.locatedo.testing.FakeSyncStateRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.util.Log, which the failure paths write to.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SharingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = Instant.parse("2026-10-06T00:00:00Z")
    private val memberships = FakeMembershipRepository()
    private val syncState = FakeSyncStateRepository(SyncState(householdId = UUID.randomUUID(), plan = Plan.PRO))
    private val households = FakeHouseholdManager()
    private val sync = FakeSyncEngine()
    private val me = Membership(testSession.userId, MemberRole.OWNER, "Taro", now, now)
    private val other = Membership(UUID.randomUUID(), MemberRole.MEMBER, "Hanako", now, now)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signedOutShowsTheIntro() = runTest(dispatcher) {
        val viewModel = viewModel(signedIn = false)

        assertFalse(viewModel.uiState.value.isSignedIn)
        assertNull(viewModel.uiState.value.status)
    }

    @Test
    fun anOwnerWithoutProSeesTheProRequirement() = runTest(dispatcher) {
        syncState.state.value = SyncState(householdId = UUID.randomUUID(), plan = Plan.FREE)
        memberships.state.value = listOf(me)
        val viewModel = viewModel()

        assertEquals(SharingStatus.OWNER_FREE, viewModel.uiState.value.status)
    }

    @Test
    fun anOwnerAloneCanInviteFiveMore() = runTest(dispatcher) {
        memberships.state.value = listOf(me)
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertEquals(SharingStatus.OWNER_ALONE, state.status)
        assertEquals(5, state.seatsLeft)
        assertTrue(state.canAcceptInvite)
        assertFalse(state.canLeave)
    }

    @Test
    fun anOwnerSharingCanRemoveOthersButNotThemselves() = runTest(dispatcher) {
        memberships.state.value = listOf(me, other)
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertEquals(SharingStatus.OWNER_SHARING, state.status)
        assertTrue(state.canRemove(other))
        assertFalse(state.canRemove(me))
        assertFalse(state.canAcceptInvite)
        assertFalse(state.canLeave)
    }

    @Test
    fun aMemberSeesTheOwnerAndCanLeave() = runTest(dispatcher) {
        memberships.state.value = listOf(other.copy(role = MemberRole.OWNER), me.copy(role = MemberRole.MEMBER))
        val viewModel = viewModel()

        val state = viewModel.uiState.value
        assertEquals(SharingStatus.MEMBER, state.status)
        assertEquals("Hanako", state.owner?.displayName)
        assertTrue(state.canLeave)
        assertFalse(state.canRemove(other))
    }

    @Test
    fun openingSyncsAndAnInviteIsHandedToTheShareSheet() = runTest(dispatcher) {
        memberships.state.value = listOf(me)
        val viewModel = viewModel()
        val events = mutableListOf<SharingEvent>()
        backgroundScope.launch { viewModel.events.collect { events += it } }
        assertEquals(1, sync.syncs)

        viewModel.createInvite()

        assertEquals(listOf(SharingEvent.InviteCreated(households.invite)), events)
    }

    @Test
    fun aFailureIsShownAndClearedByTheNextAttempt() = runTest(dispatcher) {
        memberships.state.value = listOf(me, other)
        val viewModel = viewModel()
        households.failure = IllegalStateException("down")

        viewModel.remove(other.userId)
        assertTrue(viewModel.uiState.value.hasFailed)

        households.failure = null
        viewModel.remove(other.userId)

        assertFalse(viewModel.uiState.value.hasFailed)
        assertEquals(listOf(other.userId), households.removed)
    }

    private fun TestScope.viewModel(signedIn: Boolean = true): SharingViewModel {
        val authenticator = fakeAuthenticator(if (signedIn) testSession else null)
        val viewModel = SharingViewModel(memberships, syncState, authenticator, households, sync)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }
}
