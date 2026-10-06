package com.locatedo.locatedo.core.common

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DistanceFormattingTest {
    @Test
    fun metricLocalesShowMetersThenKilometers() {
        assertEquals("100 m", format(100.0, Locale.JAPAN))
        assertEquals("1.5 km", format(1_500.0, Locale.JAPAN))
    }

    @Test
    fun theUnitedStatesShowsFeetThenMiles() {
        assertEquals("330 ft", format(100.0, Locale.US))
        assertEquals("0.3 mi", format(500.0, Locale.US))
    }

    private fun format(meters: Double, locale: Locale): String =
        DistanceFormatting.string(meters, locale).replace(' ', ' ').replace(' ', ' ')
}
