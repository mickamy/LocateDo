package com.locatedo.locatedo.core.sync

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NetworkMonitorTest {
    private val monitor = NetworkMonitor(ApplicationProvider.getApplicationContext())

    @Test
    fun theFirstNetworkDoesNotCountAsAReconnect() = runTest {
        val reconnects = count()

        monitor.pathChanged(satisfied = true)

        assertEquals(0, reconnects.value)
    }

    @Test
    fun reconnectingFiresOncePerRecovery() = runTest {
        val reconnects = count()

        monitor.pathChanged(satisfied = false)
        monitor.pathChanged(satisfied = false)
        monitor.pathChanged(satisfied = true)
        monitor.pathChanged(satisfied = true)
        monitor.pathChanged(satisfied = false)
        monitor.pathChanged(satisfied = true)

        assertEquals(2, reconnects.value)
    }

    private fun TestScope.count(): Counter {
        val counter = Counter()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { monitor.reconnected.collect { counter.value += 1 } }
        return counter
    }

    private class Counter(var value: Int = 0)
}
