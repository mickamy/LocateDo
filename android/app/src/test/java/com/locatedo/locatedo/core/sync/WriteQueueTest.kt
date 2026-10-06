package com.locatedo.locatedo.core.sync

import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.inMemoryDatabase
import com.locatedo.locatedo.core.data.place
import com.locatedo.locatedo.core.data.queuedWrites
import com.locatedo.locatedo.core.database.LocateDoDatabase
import com.locatedo.locatedo.testing.fakeAuthenticator
import com.locatedo.locatedo.testing.testSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WriteQueueTest {
    private lateinit var database: LocateDoDatabase
    private lateinit var queue: WriteQueue
    private val authenticator = fakeAuthenticator()

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        queue = WriteQueue(database.pendingWriteDao(), authenticator, fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun signedOutWritesAreNotQueued() = runTest {
        val queued = countQueued()

        queue.enqueue(Write.put(place("Store")))

        assertTrue(database.queuedWrites().isEmpty())
        assertEquals(0, queued.value)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun enqueueNumbersWritesInOrder() = runTest {
        authenticator.signIn(testSession)
        val queued = countQueued()
        val first = place("Store")
        val second = place("Office")

        queue.enqueue(Write.put(first))
        queue.enqueue(listOf(Write.put(second), Write.deletePlace(first.id)))

        val stored = database.pendingWriteDao().all()
        assertEquals(listOf(1L, 2L, 3L), stored.map { it.sequence })
        assertEquals(listOf("putPlace", "putPlace", "deletePlace"), stored.map { it.kind })
        assertEquals(listOf(0, 0, 0), stored.map { it.attempts })
        assertEquals(Write.put(first), database.queuedWrites().first())
        assertEquals(2, queued.value)
        assertTrue(!queue.isEmpty())
    }

    @Test
    fun sequenceContinuesAfterTheHeadIsRemoved() = runTest {
        authenticator.signIn(testSession)
        val store = place("Store")
        queue.enqueue(Write.put(store))
        queue.enqueue(Write.put(store))

        database.pendingWriteDao().delete(checkNotNull(database.pendingWriteDao().head()).sequence)
        queue.enqueue(Write.deletePlace(store.id))

        assertEquals(listOf(2L, 3L), database.pendingWriteDao().all().map { it.sequence })
    }

    @Test
    fun anEmptyListQueuesNothing() = runTest {
        authenticator.signIn(testSession)
        val queued = countQueued()

        queue.enqueue(emptyList())

        assertTrue(queue.isEmpty())
        assertEquals(0, queued.value)
    }

    @Test
    fun headIsNullWhenEmpty() = runTest {
        assertNull(database.pendingWriteDao().head())
    }

    private fun TestScope.countQueued(): Counter {
        val counter = Counter()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { queue.queued.collect { counter.value += 1 } }
        return counter
    }

    private class Counter(var value: Int = 0)
}
