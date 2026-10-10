package com.locatedo.locatedo.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsQueueTest {
    @Test
    fun holdsUntilTheAnswerIsKnown() {
        val queue = AnalyticsQueue()

        assertFalse(queue.submit(event("first")))
        assertFalse(queue.submit(AnalyticsQueue.Entry.UserProperty("plan", "free")))

        assertTrue(queue.decide(null).isEmpty())
        assertEquals(2, queue.entries.size)
    }

    @Test
    fun handsBackWhatWasHeldInOrderOnceGranted() {
        val queue = AnalyticsQueue()
        queue.submit(event("first"))
        queue.submit(event("second"))

        val released = queue.decide(true)

        assertEquals(listOf(event("first"), event("second")), released)
        assertTrue(queue.entries.isEmpty())
        assertTrue(queue.submit(event("third")))
    }

    @Test
    fun dropsWhatWasHeldOnceDeclined() {
        val queue = AnalyticsQueue()
        queue.submit(event("first"))

        assertTrue(queue.decide(false).isEmpty())
        assertFalse(queue.submit(event("second")))
        assertTrue(queue.entries.isEmpty())
    }

    @Test
    fun holdsAgainWhenTheAnswerIsNoLongerKnown() {
        val queue = AnalyticsQueue()
        queue.decide(true)

        queue.decide(null)

        assertFalse(queue.submit(event("first")))
        assertEquals(1, queue.entries.size)
    }

    @Test
    fun keepsOnlyTheFirstEntriesUpToTheLimit() {
        val queue = AnalyticsQueue()
        repeat(AnalyticsQueue.LIMIT + 5) { queue.submit(event("event$it")) }

        val released = queue.decide(true)

        assertEquals(AnalyticsQueue.LIMIT, released.size)
        assertEquals(event("event0"), released.first())
    }

    private fun event(name: String) = AnalyticsQueue.Entry.Event(name, emptyMap())
}
