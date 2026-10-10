package com.locatedo.locatedo.core.analytics

import java.util.Locale

// The EEA and the UK, where reading the app instance id for analytics needs consent first.
object ConsentRegion {
    private val countries = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
        "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE",
        "IS", "LI", "NO", "GB",
    )

    // Play Billing gives the store country as ISO 3166-1 alpha-2, like the device region.
    fun requiresConsent(storeCountry: String?, region: String?): Boolean {
        if (!storeCountry.isNullOrEmpty()) {
            return storeCountry.uppercase(Locale.ROOT) in countries
        }
        if (!region.isNullOrEmpty()) {
            return region.uppercase(Locale.ROOT) in countries
        }
        return true
    }

    val currentRegion: String?
        get() = Locale.getDefault().country.ifEmpty { null }
}
