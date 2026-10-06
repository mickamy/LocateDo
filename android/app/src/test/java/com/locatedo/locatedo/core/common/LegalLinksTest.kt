package com.locatedo.locatedo.core.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LegalLinksTest {
    @Test
    fun japaneseGetsTheJapanesePolicy() {
        assertEquals("https://locatedo.com/privacy-ja", LegalLinks.privacyPolicy(Locale.JAPAN))
        assertEquals("https://locatedo.com/privacy-ja", LegalLinks.privacyPolicy(Locale.forLanguageTag("ja")))
    }

    @Test
    fun everyOtherLanguageGetsTheEnglishPolicy() {
        assertEquals("https://locatedo.com/privacy", LegalLinks.privacyPolicy(Locale.US))
        assertEquals("https://locatedo.com/privacy", LegalLinks.privacyPolicy(Locale.FRANCE))
    }
}
