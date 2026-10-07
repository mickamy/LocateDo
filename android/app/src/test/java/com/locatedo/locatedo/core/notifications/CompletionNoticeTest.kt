package com.locatedo.locatedo.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletionNoticeTest {
    @Test
    fun readsANotice() {
        val data = mapOf("type" to "completion", "body" to "Ben checked off \"Milk\" and 1 more", "count" to "2")

        assertEquals(CompletionNotice("Ben checked off \"Milk\" and 1 more", 2), CompletionNotice.from(data))
    }

    @Test
    fun aMissingOrBrokenCountIsOne() {
        assertEquals(1, CompletionNotice.from(mapOf("type" to "completion", "body" to "Ben checked off \"Milk\""))?.count)
        assertEquals(1, CompletionNotice.from(mapOf("type" to "completion", "body" to "Body", "count" to "x"))?.count)
    }

    @Test
    fun otherMessagesAndEmptyBodiesAreNotNotices() {
        assertNull(CompletionNotice.from(mapOf("type" to "campaign", "body" to "Body")))
        assertNull(CompletionNotice.from(mapOf("type" to "completion", "body" to "")))
        assertNull(CompletionNotice.from(emptyMap()))
    }
}
