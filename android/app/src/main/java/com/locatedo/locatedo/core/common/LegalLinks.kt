package com.locatedo.locatedo.core.common

import java.util.Locale

object LegalLinks {
    fun privacyPolicy(locale: Locale): String {
        if (locale.language == "ja") {
            return "https://locatedo.com/privacy-ja"
        }
        return "https://locatedo.com/privacy"
    }

    fun termsOfUse(locale: Locale): String {
        if (locale.language == "ja") {
            return "https://locatedo.com/terms-ja"
        }
        return "https://locatedo.com/terms"
    }

    // Play's subscription center; the app's own subscription is listed there once bought.
    const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

    // The store listing, for the update-required screen.
    const val PLAY_LISTING = "https://play.google.com/store/apps/details?id=com.locatedo.locatedo"
}
