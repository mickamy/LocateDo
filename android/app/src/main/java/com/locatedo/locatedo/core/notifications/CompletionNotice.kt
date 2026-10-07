package com.locatedo.locatedo.core.notifications

// Someone in the household checked off to-dos this user added, sent as an FCM data message:
// {"type":"completion","body","count"}. The server writes the text in the device's language, with no title.
data class CompletionNotice(val body: String, val count: Int) {
    companion object {
        fun from(data: Map<String, String>): CompletionNotice? {
            if (data["type"] != "completion") {
                return null
            }
            val body = data["body"].orEmpty()
            if (body.isEmpty()) {
                return null
            }
            val count = data["count"]?.toIntOrNull() ?: 1
            return CompletionNotice(body, count.coerceAtLeast(1))
        }
    }
}
