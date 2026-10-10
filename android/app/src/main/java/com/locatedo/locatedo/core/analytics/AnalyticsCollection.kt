package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.billing.Entitlements
import javax.inject.Inject
import javax.inject.Singleton

// Where the consent answer takes effect: Firebase Analytics, Crashlytics, and the id RevenueCat forwards events with.
interface AnalyticsCollection {
    suspend fun apply(decision: Boolean?)
}

@Singleton
class FirebaseAnalyticsCollection @Inject constructor(
    private val sink: FirebaseAnalyticsSink,
    private val crashReporting: CrashReporting,
    private val entitlements: Entitlements,
) : AnalyticsCollection {
    override suspend fun apply(decision: Boolean?) {
        sink.apply(decision)
        crashReporting.apply(decision)
        entitlements.analyticsIdChanged()
    }
}
