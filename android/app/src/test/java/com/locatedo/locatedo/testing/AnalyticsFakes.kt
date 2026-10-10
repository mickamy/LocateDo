package com.locatedo.locatedo.testing

import com.locatedo.locatedo.core.analytics.Analytics
import com.locatedo.locatedo.core.analytics.AnalyticsCollection
import com.locatedo.locatedo.core.analytics.AnalyticsConsent
import com.locatedo.locatedo.core.analytics.AnalyticsEvent
import com.locatedo.locatedo.core.analytics.AnalyticsParameters
import com.locatedo.locatedo.core.analytics.AnalyticsScreen
import com.locatedo.locatedo.core.analytics.AnalyticsUserProperty
import com.locatedo.locatedo.core.analytics.StoreCountrySource
import com.locatedo.locatedo.core.analytics.wireValues
import com.locatedo.locatedo.core.datastore.AppPreferences
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeAnalytics : Analytics {
    val events = mutableListOf<Pair<AnalyticsEvent, Map<String, Any>>>()
    val screens = mutableListOf<Pair<AnalyticsScreen, Map<String, Any>>>()
    val userProperties = mutableMapOf<AnalyticsUserProperty, String?>()
    var instanceId: String? = "firebase-instance-1"

    val names: List<AnalyticsEvent>
        get() = events.map { it.first }

    fun values(event: AnalyticsEvent): Map<String, Any> = events.last { it.first == event }.second

    fun count(event: AnalyticsEvent): Int = events.count { it.first == event }

    override fun log(event: AnalyticsEvent, parameters: AnalyticsParameters) {
        events += event to parameters.wireValues()
    }

    override fun logScreen(screen: AnalyticsScreen, parameters: AnalyticsParameters) {
        screens += screen to parameters.wireValues()
    }

    override fun setUserProperty(property: AnalyticsUserProperty, value: String?) {
        userProperties[property] = value
    }

    override suspend fun appInstanceId(): String? = instanceId
}

class SettableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

class FakeAnalyticsCollection : AnalyticsCollection {
    val decisions = mutableListOf<Boolean?>()

    override suspend fun apply(decision: Boolean?) {
        decisions += decision
    }
}

class FakeStoreCountry : StoreCountrySource {
    var country: String? = null

    override suspend fun country(): String? = country
}

fun fakeAnalyticsConsent(
    preferences: AppPreferences,
    analytics: FakeAnalytics = FakeAnalytics(),
    storeCountry: String? = "US",
): AnalyticsConsent = AnalyticsConsent(
    preferences,
    FakeStoreCountry().also { it.country = storeCountry },
    FakeAnalyticsCollection(),
    analytics,
)
