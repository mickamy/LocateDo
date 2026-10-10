package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.BuildConfig
import com.locatedo.locatedo.core.datastore.AnalyticsConsentRecord
import com.locatedo.locatedo.core.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Consent to usage analytics and crash reports, asked in the EEA and the UK at the end of onboarding.
@Singleton
class AnalyticsConsent @Inject constructor(
    private val preferences: AppPreferences,
    private val storeCountry: StoreCountrySource,
    private val collection: AnalyticsCollection,
    private val analytics: Analytics,
) {
    enum class Source(val key: String) {
        ONBOARDING("onboarding"),
        SETTINGS("settings"),
    }

    private val mutex = Mutex()

    val state: Flow<AnalyticsConsentState> = preferences.analyticsConsent.map(::state)

    // Applies what is known from the last launch, then checks the region again for anyone who changed country.
    suspend fun start() {
        mutex.withLock {
            collection.apply(current().decision)
        }
        resolveRegion()
    }

    suspend fun resolveRegion() {
        val record = preferences.analyticsConsent.first()
        var country = storeCountry.country()
        if (BuildConfig.DEBUG_TOOLS && record.storeCountryOverride != null) {
            country = record.storeCountryOverride
        }
        mutex.withLock {
            val before = current()
            preferences.setAnalyticsConsentRequired(ConsentRegion.requiresConsent(country, ConsentRegion.currentRegion))
            val after = current()
            if (after.decision != before.decision) {
                collection.apply(after.decision)
            }
        }
    }

    suspend fun set(isOn: Boolean, source: Source) {
        mutex.withLock {
            val before = current()
            preferences.setAnalyticsConsent(isOn)
            val after = current()
            if (after.decision != before.decision) {
                collection.apply(after.decision)
            }
            if (isOn && before.answer != true) {
                analytics.log(AnalyticsEvent.ANALYTICS_CONSENT_GRANTED, mapOf(AnalyticsParameter.SOURCE to source.key))
            }
        }
    }

    private suspend fun current(): AnalyticsConsentState = state(preferences.analyticsConsent.first())

    private fun state(record: AnalyticsConsentRecord): AnalyticsConsentState = AnalyticsConsentState(
        answer = record.answer,
        required = record.required,
        estimate = ConsentRegion.requiresConsent(storeCountry = null, region = ConsentRegion.currentRegion),
    )
}
