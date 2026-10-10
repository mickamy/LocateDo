package com.locatedo.locatedo.core.analytics

import com.locatedo.locatedo.core.datastore.AppPreferences
import com.locatedo.locatedo.testing.FakeAnalytics
import com.locatedo.locatedo.testing.FakeAnalyticsCollection
import com.locatedo.locatedo.testing.FakeStoreCountry
import com.locatedo.locatedo.testing.testPreferences
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnalyticsConsentTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val analytics = FakeAnalytics()
    private val collection = FakeAnalyticsCollection()
    private val storeCountry = FakeStoreCountry()
    private val locale = Locale.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(locale)
    }

    @Test
    fun startsSendingOutsideTheEeaAndTheUkOnceTheRegionIsKnown() = runTest {
        storeCountry.country = "US"
        val (consent, _) = consent()

        consent.start()

        assertEquals(listOf(null, true), collection.decisions)
        assertFalse(consent.state.first().needsAnswer)
    }

    @Test
    fun keepsHoldingInTheEeaAndTheUkUntilAnswered() = runTest {
        storeCountry.country = "GB"
        val (consent, _) = consent()

        consent.start()

        assertEquals(listOf<Boolean?>(null), collection.decisions)
        assertTrue(consent.state.first().needsAnswer)
    }

    @Test
    fun grantingSendsAndLogsIt() = runTest {
        storeCountry.country = "DE"
        val (consent, _) = consent()
        consent.start()

        consent.set(true, AnalyticsConsent.Source.ONBOARDING)

        assertEquals(listOf(null, true), collection.decisions)
        assertEquals(mapOf("source" to "onboarding"), analytics.values(AnalyticsEvent.ANALYTICS_CONSENT_GRANTED))
    }

    @Test
    fun decliningDropsAndLogsNothing() = runTest {
        storeCountry.country = "DE"
        val (consent, _) = consent()
        consent.start()

        consent.set(false, AnalyticsConsent.Source.ONBOARDING)

        assertEquals(listOf(null, false), collection.decisions)
        assertEquals(0, analytics.count(AnalyticsEvent.ANALYTICS_CONSENT_GRANTED))
    }

    @Test
    fun movingIntoTheEeaWithoutAnAnswerStopsSending() = runTest {
        storeCountry.country = "US"
        val (consent, _) = consent()
        consent.start()

        storeCountry.country = "FR"
        consent.resolveRegion()

        assertEquals(listOf(null, true, null), collection.decisions)
        assertTrue(consent.state.first().needsAnswer)
    }

    @Test
    fun theDebugOverrideWinsOverTheStore() = runTest {
        storeCountry.country = "US"
        val (consent, preferences) = consent()
        preferences.setConsentStoreCountryOverride("GB")

        consent.start()

        assertTrue(consent.state.first().needsAnswer)
    }

    private fun TestScope.consent(): Pair<AnalyticsConsent, AppPreferences> {
        val preferences = testPreferences(folder.root, backgroundScope)
        return AnalyticsConsent(preferences, storeCountry, collection, analytics) to preferences
    }
}
