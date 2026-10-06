package com.locatedo.locatedo.core.common

import java.util.Locale

object LegalLinks {
    fun privacyPolicy(locale: Locale): String {
        if (locale.language == "ja") {
            return "https://locatedo.com/privacy-ja"
        }
        return "https://locatedo.com/privacy"
    }
}
