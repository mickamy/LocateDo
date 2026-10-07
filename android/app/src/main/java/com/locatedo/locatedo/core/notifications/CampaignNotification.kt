package com.locatedo.locatedo.core.notifications

import java.net.URI
import java.net.URISyntaxException

// A promotional push, sent as an FCM data message: {"type":"campaign","campaign_id","title","body","url"?}.
data class CampaignNotification(val id: String, val title: String, val body: String, val url: String?) {
    companion object {
        fun from(data: Map<String, String>): CampaignNotification? {
            if (data["type"] != "campaign") {
                return null
            }
            val id = data["campaign_id"].orEmpty()
            val title = data["title"].orEmpty()
            val body = data["body"].orEmpty()
            if (id.isEmpty() || title.isEmpty() || body.isEmpty()) {
                return null
            }
            return CampaignNotification(id, title, body, httpsUrl(data["url"]))
        }

        fun httpsUrl(raw: String?): String? {
            if (raw.isNullOrEmpty()) {
                return null
            }
            val uri = try {
                URI(raw)
            } catch (_: URISyntaxException) {
                return null
            }
            if (uri.scheme != "https" || uri.host.isNullOrEmpty()) {
                return null
            }
            return raw
        }
    }
}
