package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.common.TodosRequests
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeCompletionNotifier
import com.locatedo.locatedo.testing.testPreferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompletionHandlerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val notifier = FakeCompletionNotifier()
    private val todosRequests = TodosRequests()
    private val analytics = FakeAnalytics()
    private val notice = CompletionNotice("Ben checked off \"Milk\"", 1)

    @Test
    fun postsWhileTheSwitchIsOn() = runTest {
        val (handler, _) = handler()

        handler.received(notice, id = 1)

        assertEquals(listOf(notice), notifier.notified)
    }

    @Test
    fun dropsANoticeThatCrossedTurningItOff() = runTest {
        val (handler, preferences) = handler()
        preferences.setCompletionNotices(false)

        handler.received(notice, id = 1)

        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun anOpenIsLoggedAndShowsTheTodos() = runTest {
        val (handler, _) = handler()

        handler.opened(count = 3)

        assertEquals(mapOf("count" to 3L), analytics.values(AnalyticsEvent.COMPLETION_NOTICE_OPENED))
        assertTrue(todosRequests.pending.value)
    }

    private fun TestScope.handler(): Pair<CompletionHandler, AppPreferences> {
        val preferences = testPreferences(folder.root, backgroundScope)
        return CompletionHandler(preferences, notifier, todosRequests, analytics) to preferences
    }
}
