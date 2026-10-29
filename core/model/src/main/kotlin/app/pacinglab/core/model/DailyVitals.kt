package app.pacinglab.core.model

import java.time.LocalDate

/**
 * A night's values as the watch's own app computed them, by the date of the morning after. They come
 * from a secondary source (the Google Health API) and fill in what Health Connect lacks.
 *
 * @property hrvMs the app's nightly HRV (RMSSD), the figure its own HRV screen shows.
 * @property oxygenSaturation average SpO2 over the night in per cent, with its [oxygenSaturationLow]
 *   and [oxygenSaturationHigh] bounds.
 * @property skinTemperature nightly skin temperature in degrees Celsius, with the app's own
 *   [skinTemperatureBaseline].
 */
data class DailyVitals(
    val date: LocalDate,
    val hrvMs: Double? = null,
    val restingHr: Double? = null,
    val breathingRate: Double? = null,
    val oxygenSaturation: Double? = null,
    val oxygenSaturationLow: Double? = null,
    val oxygenSaturationHigh: Double? = null,
    val skinTemperature: Double? = null,
    val skinTemperatureBaseline: Double? = null,
) {
    /** How far the night's skin temperature was from the app's baseline, in degrees Celsius. */
    val skinTemperatureDeviation: Double?
        get() = if (skinTemperature != null && skinTemperatureBaseline != null) skinTemperature - skinTemperatureBaseline else null

    /** These values, with those of [older] where these have none. */
    fun orElse(older: DailyVitals): DailyVitals = DailyVitals(
        date = date,
        hrvMs = hrvMs ?: older.hrvMs,
        restingHr = restingHr ?: older.restingHr,
        breathingRate = breathingRate ?: older.breathingRate,
        oxygenSaturation = oxygenSaturation ?: older.oxygenSaturation,
        oxygenSaturationLow = oxygenSaturationLow ?: older.oxygenSaturationLow,
        oxygenSaturationHigh = oxygenSaturationHigh ?: older.oxygenSaturationHigh,
        skinTemperature = skinTemperature ?: older.skinTemperature,
        skinTemperatureBaseline = skinTemperatureBaseline ?: older.skinTemperatureBaseline,
    )
}
