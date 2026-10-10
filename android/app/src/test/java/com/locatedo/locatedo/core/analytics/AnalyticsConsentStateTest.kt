package com.locatedo.locatedo.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentRegionTest {
    @Test
    fun prefersTheStoreCountry() {
        for (country in listOf("GB", "DE", "NO", "ie")) {
            assertTrue(country, ConsentRegion.requiresConsent(storeCountry = country, region = "US"))
        }
        for (country in listOf("US", "JP", "CH")) {
            assertFalse(country, ConsentRegion.requiresConsent(storeCountry = country, region = "GB"))
        }
    }

    @Test
    fun fallsBackToTheDeviceRegion() {
        assertTrue(ConsentRegion.requiresConsent(storeCountry = null, region = "GB"))
        assertTrue(ConsentRegion.requiresConsent(storeCountry = "", region = "fr"))
        assertFalse(ConsentRegion.requiresConsent(storeCountry = null, region = "JP"))
    }

    @Test
    fun treatsAnUnknownRegionAsInScope() {
        assertTrue(ConsentRegion.requiresConsent(storeCountry = null, region = null))
    }
}

class AnalyticsConsentStateTest {
    @Test
    fun sendsNothingBeforeTheFirstRegionCheck() {
        val state = AnalyticsConsentState(answer = null, required = null, estimate = false)

        assertNull(state.decision)
        assertFalse(state.needsAnswer)
    }

    @Test
    fun guessesFromTheDeviceRegionUntilTheStoreCountryIsKnown() {
        assertTrue(AnalyticsConsentState(answer = null, required = null, estimate = true).needsAnswer)
    }

    @Test
    fun sendsOutsideTheEeaAndTheUkWithoutAsking() {
        val state = AnalyticsConsentState(answer = null, required = false, estimate = true)

        assertEquals(true, state.decision)
        assertFalse(state.needsAnswer)
        assertFalse(state.showsSetting)
    }

    @Test
    fun asksInTheEeaAndTheUk() {
        val state = AnalyticsConsentState(answer = null, required = true, estimate = false)

        assertNull(state.decision)
        assertTrue(state.needsAnswer)
        assertTrue(state.showsSetting)
    }

    @Test
    fun followsTheAnswer() {
        assertEquals(true, AnalyticsConsentState(answer = true, required = true, estimate = true).decision)
        assertEquals(false, AnalyticsConsentState(answer = false, required = true, estimate = true).decision)
    }

    @Test
    fun anAnswerOutlastsAMoveOutOfScope() {
        val state = AnalyticsConsentState(answer = false, required = false, estimate = false)

        assertEquals(false, state.decision)
        assertTrue(state.showsSetting)
    }
}
