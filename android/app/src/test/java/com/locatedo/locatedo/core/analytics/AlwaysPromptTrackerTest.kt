package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.SettableClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AlwaysPromptTrackerTest {
    private val analytics = FakeAnalytics()
    private val clock = SettableClock(Instant.parse("2026-10-06T03:00:00Z"))
    private val tracker = AlwaysPromptTracker(analytics, clock)

    @Test
    fun eachWayOfClosingIsItsOwnResult() {
        for (answer in AlwaysPromptAnswer.entries) {
            tracker.shown()
            clock.now = clock.now.plusSeconds(2)

            tracker.answered(answer)

            assertEquals(
                mapOf("result" to answer.key, "duration_s" to 2L),
                analytics.values(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED),
            )
        }
        assertEquals(listOf("allow", "later", "dismissed"), AlwaysPromptAnswer.entries.map { it.key })
    }

    @Test
    fun onlyTheFirstAnswerOfAShowingCounts() {
        tracker.shown()

        tracker.answered(AlwaysPromptAnswer.ALLOW)
        tracker.answered(AlwaysPromptAnswer.DISMISSED)

        assertEquals(1, analytics.count(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED))
        assertEquals("allow", analytics.values(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED)["result"])
    }

    @Test
    fun nothingIsLoggedForASheetThatWasNotShown() {
        tracker.answered(AlwaysPromptAnswer.LATER)

        assertEquals(0, analytics.count(AnalyticsEvent.ALWAYS_PROMPT_ANSWERED))
    }
}
