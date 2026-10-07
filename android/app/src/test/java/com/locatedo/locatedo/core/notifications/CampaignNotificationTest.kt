package com.locatedo.locatedo.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampaignNotificationTest {
    private val message = mapOf(
        "type" to "campaign",
        "campaign_id" to "0199b2c4-0000-7000-8000-000000000001",
        "title" to "New in StopBy",
        "body" to "Share lists with your family.",
        "url" to "https://locatedo.com/news",
    )

    @Test
    fun readsACampaign() {
        assertEquals(
            CampaignNotification(
                id = "0199b2c4-0000-7000-8000-000000000001",
                title = "New in StopBy",
                body = "Share lists with your family.",
                url = "https://locatedo.com/news",
            ),
            CampaignNotification.from(message),
        )
    }

    @Test
    fun theUrlIsOptional() {
        assertNull(CampaignNotification.from(message - "url")?.url)
    }

    @Test
    fun onlyHttpsLinksAreKept() {
        assertNull(CampaignNotification.from(message + ("url" to "http://locatedo.com/news"))?.url)
        assertNull(CampaignNotification.from(message + ("url" to "intent://scan#Intent;end"))?.url)
        assertNull(CampaignNotification.from(message + ("url" to "not a url"))?.url)
    }

    @Test
    fun otherMessagesAreNotCampaigns() {
        assertNull(CampaignNotification.from(mapOf("reason" to "sync")))
    }

    @Test
    fun aCampaignWithoutTextIsDropped() {
        assertNull(CampaignNotification.from(message - "title"))
        assertNull(CampaignNotification.from(message + ("body" to "")))
        assertNull(CampaignNotification.from(message - "campaign_id"))
    }
}
