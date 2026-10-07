package com.locatedo.locatedo.core.notifications

import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.data.fixedClock
import com.locatedo.locatedo.core.data.fixedNow
import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeCampaignNotifier
import com.locatedo.locatedo.testing.testPreferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CampaignHandlerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val notifier = FakeCampaignNotifier()
    private val analytics = FakeAnalytics()
    private val campaign = CampaignNotification("campaign-1", "Title", "Body", url = null)
    private val sentAt = fixedNow.minusSeconds(90)

    @Test
    fun postsWhileConsentIsOn() = runTest {
        val (handler, preferences) = handler()
        preferences.setPromotionsConsent(true)

        handler.received(campaign, sentAt)

        assertEquals(listOf(campaign to sentAt), notifier.notified)
    }

    @Test
    fun dropsACampaignThatCrossedAWithdrawal() = runTest {
        val (handler, _) = handler()

        handler.received(campaign, sentAt)

        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun opensAreLoggedWithTheTimeSinceSending() = runTest {
        val (handler, _) = handler()

        handler.opened("campaign-1", hasUrl = true, sentAt = sentAt)

        assertEquals(
            mapOf("campaign_id" to "campaign-1", "has_url" to 1L, "latency_s" to 90L),
            analytics.values(AnalyticsEvent.CAMPAIGN_OPENED),
        )
    }

    private fun TestScope.handler(): Pair<CampaignHandler, AppPreferences> {
        val preferences = testPreferences(folder.root, backgroundScope)
        return CampaignHandler(preferences, notifier, analytics, fixedClock) to preferences
    }
}
