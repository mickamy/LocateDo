package com.locatedo.locatedo.core.appstatus

import com.locatedo.locatedo.core.appstatus.AppStatusDocumentTest.Companion.ENDS
import com.locatedo.locatedo.core.appstatus.AppStatusDocumentTest.Companion.FULL_JSON
import com.locatedo.locatedo.core.appstatus.AppStatusDocumentTest.Companion.STARTS
import com.locatedo.locatedo.testing.FakeAppStatusFetcher
import com.locatedo.locatedo.testing.SettableClock
import com.locatedo.locatedo.testing.appStatusStore
import com.locatedo.locatedo.testing.testPreferences
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for android.util.Log, which the store calls when a fetch fails.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppStatusStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val longBefore = Instant.parse("2026-10-06T00:00:00Z")

    @Test
    fun aFetchedStatusIsKeptForWhenTheNextFetchFails() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        val first = appStatusStore(preferences, backgroundScope, FakeAppStatusFetcher(FULL_JSON), SettableClock(longBefore), version = "1.1.9")
        first.refresh()
        assertTrue(first.state.value.requiresUpdate)

        val gate = MaintenanceGate(SettableClock(STARTS))
        val second = appStatusStore(preferences, backgroundScope, FakeAppStatusFetcher(null), SettableClock(STARTS), version = "1.1.9", gate = gate)
        second.refresh()

        assertTrue(second.state.value.requiresUpdate)
        assertTrue(gate.isClosed())
    }

    @Test
    fun withoutAnyStatusNothingIsRestricted() = runTest {
        val gate = MaintenanceGate(SettableClock(STARTS))
        val store = appStatusStore(testPreferences(folder.root, backgroundScope), backgroundScope, FakeAppStatusFetcher(null), SettableClock(STARTS), gate = gate)

        store.refresh()

        assertFalse(store.state.value.requiresUpdate)
        assertEquals(MaintenancePhase.None, store.state.value.maintenancePhase)
        assertNull(store.state.value.pendingNotice)
        assertFalse(gate.isClosed())
    }

    @Test
    fun theUpcomingBannerStaysDismissedUntilTheWindowChanges() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        val clock = SettableClock(STARTS.minusSeconds(60))
        val store = appStatusStore(preferences, backgroundScope, FakeAppStatusFetcher(FULL_JSON), clock, version = "1.2.0")
        store.refresh()
        assertTrue(store.state.value.showsUpcomingBanner)

        store.dismissUpcomingBanner()
        assertFalse(store.state.value.showsUpcomingBanner)

        val moved = FakeAppStatusFetcher(FULL_JSON.replace("17:00:00Z", "18:00:00Z"))
        val reloaded = appStatusStore(preferences, backgroundScope, moved, clock, version = "1.2.0")
        reloaded.refresh()
        assertTrue(reloaded.state.value.showsUpcomingBanner)
    }

    @Test
    fun aNoticeIsShownOnceAndOnlyUntilItsDate() = runTest {
        val preferences = testPreferences(folder.root, backgroundScope)
        val store = appStatusStore(preferences, backgroundScope, FakeAppStatusFetcher(FULL_JSON), SettableClock(STARTS), version = "1.2.0")
        store.refresh()
        val notice = checkNotNull(store.state.value.pendingNotice)

        store.markNoticeShown(notice)
        assertNull(store.state.value.pendingNotice)

        val again = appStatusStore(preferences, backgroundScope, FakeAppStatusFetcher(FULL_JSON), SettableClock(STARTS), version = "1.2.0")
        again.refresh()
        assertNull(again.state.value.pendingNotice)

        val late = appStatusStore(testPreferences(folder.newFolder(), backgroundScope), backgroundScope, FakeAppStatusFetcher(FULL_JSON), SettableClock(notice.until), version = "1.2.0")
        late.refresh()
        assertNull(late.state.value.pendingNotice)
    }

    @Test
    fun onlyAnActiveWindowCountsAsActive() = runTest {
        val before = appStatusStore(testPreferences(folder.newFolder(), backgroundScope), backgroundScope, FakeAppStatusFetcher(FULL_JSON), SettableClock(STARTS.minusSeconds(1)), version = "1.2.0")
        before.refresh()
        assertNull(before.state.value.activeMaintenance)

        val during = appStatusStore(testPreferences(folder.newFolder(), backgroundScope), backgroundScope, FakeAppStatusFetcher(FULL_JSON), SettableClock(STARTS), version = "1.2.0")
        during.refresh()
        assertEquals(ENDS, during.state.value.activeMaintenance?.endsAt)
    }

    @Test
    fun syncResumesWhenTheWindowEnds() = runTest {
        val clock = SettableClock(ENDS.minusMillis(200))
        val gate = MaintenanceGate(clock)
        val store = appStatusStore(testPreferences(folder.root, backgroundScope), backgroundScope, FakeAppStatusFetcher(FULL_JSON), clock, version = "1.2.0", gate = gate)
        var ended = 0
        backgroundScope.launch { store.maintenanceEnded.collect { ended += 1 } }
        store.refresh()
        assertTrue(gate.isClosed())
        assertTrue(store.state.value.maintenancePhase is MaintenancePhase.Active)

        clock.now = ENDS
        advanceTimeBy(200)
        runCurrent()

        assertEquals(MaintenancePhase.None, store.state.value.maintenancePhase)
        assertFalse(gate.isClosed())
        assertEquals(1, ended)
    }
}
