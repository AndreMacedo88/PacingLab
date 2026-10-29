package app.pacinglab.core.googlehealth

import app.pacinglab.core.model.DailyVitals
import org.json.JSONObject
import java.time.LocalDate

/** The Google Health API's daily data types that make up [DailyVitals], with how to read each data point. */
internal enum class DailyType(val id: String, private val field: String) {
    HRV("daily-heart-rate-variability", "dailyHeartRateVariability"),
    RESTING_HR("daily-resting-heart-rate", "dailyRestingHeartRate"),
    BREATHING("daily-respiratory-rate", "dailyRespiratoryRate"),
    OXYGEN("daily-oxygen-saturation", "dailyOxygenSaturation"),
    TEMPERATURE("daily-sleep-temperature-derivations", "dailySleepTemperatureDerivations"),

    /** Fitbit's cardio fitness of the day; needs the activity and fitness scope, and is read further back. */
    VO2MAX("daily-vo2-max", "dailyVo2Max"),
    ;

    /** The day's values in [point], or null when it holds none of this type. */
    fun read(point: JSONObject): DailyVitals? {
        val value = point.optJSONObject(field) ?: return null
        val date = value.optJSONObject("date")?.let { LocalDate.of(it.getInt("year"), it.getInt("month"), it.getInt("day")) } ?: return null
        return when (this) {
            HRV -> value.number("averageHeartRateVariabilityMilliseconds")?.let { DailyVitals(date, hrvMs = it) }

            // Only values derived from sleep: the others come from daytime wear and run high.
            RESTING_HR -> value.number("beatsPerMinute")
                ?.takeIf { value.optJSONObject("dailyRestingHeartRateMetadata")?.optString("calculationMethod") == "WITH_SLEEP" }
                ?.let { DailyVitals(date, restingHr = it) }

            BREATHING -> value.number("breathsPerMinute")?.let { DailyVitals(date, breathingRate = it) }

            OXYGEN -> value.number("averagePercentage")?.let {
                DailyVitals(
                    date,
                    oxygenSaturation = it,
                    oxygenSaturationLow = value.number("lowerBoundPercentage"),
                    oxygenSaturationHigh = value.number("upperBoundPercentage"),
                )
            }

            TEMPERATURE -> value.number("nightlyTemperatureCelsius")?.let {
                DailyVitals(date, skinTemperature = it, skinTemperatureBaseline = value.number("baselineTemperatureCelsius"))
            }

            VO2MAX -> value.number("vo2Max")?.let { DailyVitals(date, vo2max = it, vo2maxVariance = value.number("vo2MaxCovariance")) }
        }
    }
}

/** A page of the API's response: its days' values and the token of the next, older page. */
internal class DailyPage(val days: List<DailyVitals>, val next: String?) {
    companion object {
        /** Reads [text]; throws [org.json.JSONException] when it is not a page of data points. */
        fun parse(text: String, type: DailyType): DailyPage {
            val json = JSONObject(text)
            val points = json.optJSONArray("dataPoints")
            val days = if (points == null) emptyList() else (0 until points.length()).mapNotNull { type.read(points.getJSONObject(it)) }
            return DailyPage(days, json.optString("nextPageToken").takeIf { it.isNotEmpty() })
        }
    }
}

/** Numbers come as JSON numbers, or as strings for 64-bit integers ("57"). */
private fun JSONObject.number(name: String): Double? {
    val value: Any? = opt(name)
    return (value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull()
}
