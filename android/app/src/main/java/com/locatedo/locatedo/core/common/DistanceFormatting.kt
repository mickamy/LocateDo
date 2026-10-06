package com.locatedo.locatedo.core.common

import android.icu.text.MeasureFormat
import android.icu.util.LocaleData
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.util.Locale

// Short road-style distances in the locale's system, like iOS's `.road` usage: "330 ft", "1.2 mi", "100 m", "1.5 km".
object DistanceFormatting {
    private const val METERS_PER_FOOT = 0.3048
    private const val METERS_PER_MILE = 1_609.344
    private const val FEET_UNTIL_MILES = 1_000.0
    private const val METERS_UNTIL_KILOMETERS = 1_000.0

    fun string(meters: Double, locale: Locale = Locale.getDefault()): String {
        val measure = if (usesUsCustomary(locale)) {
            val feet = meters / METERS_PER_FOOT
            if (feet < FEET_UNTIL_MILES) {
                Measure(feet.roundToTens(), MeasureUnit.FOOT)
            } else {
                Measure(meters / METERS_PER_MILE, MeasureUnit.MILE)
            }
        } else if (meters < METERS_UNTIL_KILOMETERS) {
            Measure(meters.roundToTens(), MeasureUnit.METER)
        } else {
            Measure(meters / METERS_UNTIL_KILOMETERS, MeasureUnit.KILOMETER)
        }
        val format = MeasureFormat.getInstance(ULocale.forLocale(locale), MeasureFormat.FormatWidth.SHORT, numberFormat(locale))
        return format.format(measure)
    }

    fun usesUsCustomary(locale: Locale): Boolean =
        LocaleData.getMeasurementSystem(ULocale.forLocale(locale)) == LocaleData.MeasurementSystem.US

    private fun numberFormat(locale: Locale): android.icu.text.NumberFormat =
        android.icu.text.NumberFormat.getInstance(ULocale.forLocale(locale)).apply { maximumFractionDigits = 1 }

    private fun Double.roundToTens(): Double = Math.round(this / 10.0) * 10.0
}
