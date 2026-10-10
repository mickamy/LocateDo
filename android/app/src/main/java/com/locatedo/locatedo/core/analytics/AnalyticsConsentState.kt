package com.locatedo.locatedo.core.analytics

// answer is null until the person answers; required is the last region check, null before the first one.
data class AnalyticsConsentState(
    val answer: Boolean?,
    val required: Boolean?,
    val estimate: Boolean,
) {
    val requiresConsent: Boolean
        get() = required ?: estimate

    // null until it is known whether to send: before the first region check, or in scope without an answer. Nothing
    // is sent before the first region check, so a store country in the EEA or the UK is never missed.
    val decision: Boolean?
        get() {
            if (answer != null) {
                return answer
            }
            if (required == null || required) {
                return null
            }
            return true
        }

    val isSending: Boolean
        get() = decision == true

    val needsAnswer: Boolean
        get() = requiresConsent && answer == null

    // Kept after a move out of scope, so an earlier answer can still be changed.
    val showsSetting: Boolean
        get() = requiresConsent || answer != null
}
