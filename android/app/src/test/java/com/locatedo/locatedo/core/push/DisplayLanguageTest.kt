package com.locatedo.locatedo.core.push

import android.os.LocaleList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisplayLanguageTest {
    @Test
    fun japaneseIsJapanese() {
        assertEquals("ja", displayLanguage(LocaleList.forLanguageTags("ja-JP")))
    }

    @Test
    fun englishInAnyRegionIsEnglish() {
        assertEquals("en", displayLanguage(LocaleList.forLanguageTags("en-GB,ja-JP")))
    }

    @Test
    fun theFirstTranslatedLanguageInTheListWins() {
        assertEquals("ja", displayLanguage(LocaleList.forLanguageTags("fr-FR,ja-JP,en-US")))
    }

    @Test
    fun anUntranslatedLanguageFallsBackToEnglish() {
        assertEquals("en", displayLanguage(LocaleList.forLanguageTags("fr-FR")))
    }
}
