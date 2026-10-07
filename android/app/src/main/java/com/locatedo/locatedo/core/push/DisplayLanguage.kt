package com.locatedo.locatedo.core.push

import android.content.Context
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

interface DisplayLanguage {
    fun current(): String
}

class ResourcesDisplayLanguage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DisplayLanguage {
    override fun current(): String = displayLanguage(context.resources.configuration.locales)
}

private val translations = arrayOf("en", "ja")

// Resources resolve against the whole locale list, so a user with [fr, ja] reads Japanese; anything else falls back
// to English, as the strings do.
fun displayLanguage(locales: LocaleList): String {
    val match = locales.getFirstMatch(translations) ?: return "en"
    if (match.language == Locale.JAPANESE.language) {
        return "ja"
    }
    return "en"
}
