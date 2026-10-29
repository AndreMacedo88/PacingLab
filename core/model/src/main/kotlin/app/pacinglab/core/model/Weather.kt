package app.pacinglab.core.model

import java.time.Instant
import java.time.ZoneOffset

/** A point on the earth, in degrees. */
data class Coordinates(val latitude: Double, val longitude: Double)

/** The sky in a few words, from the WMO weather codes forecasts use. */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    ;

    companion object {
        /** The condition of WMO code [code] (WMO 4677 as used by forecast models), or null for an unknown code. */
        fun ofWmo(code: Int): WeatherCondition? = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> null
        }
    }
}

/**
 * The weather at one moment, observed or forecast.
 *
 * @property temperature air temperature 2 m above ground, in degrees Celsius.
 * @property apparentTemperature how warm it feels with wind and humidity, in degrees Celsius.
 * @property dewPoint in degrees Celsius; the measure of humidity that matters for heat while running.
 * @property windSpeed mean wind 10 m above ground, in metres per second.
 * @property windGusts strongest gusts in metres per second.
 * @property precipitation in millimetres over the preceding hour.
 * @property precipitationChance the chance of precipitation in per cent; null for the current weather.
 * @property daylight whether the sun is up, for the day or night icon.
 */
data class WeatherReading(
    val time: Instant,
    val condition: WeatherCondition,
    val temperature: Double,
    val apparentTemperature: Double,
    val dewPoint: Double,
    val windSpeed: Double,
    val windGusts: Double?,
    val precipitation: Double,
    val precipitationChance: Int?,
    val daylight: Boolean,
)

/**
 * A forecast for one place.
 *
 * @property place the coordinates it was asked for, rounded.
 * @property offset the place's offset from UTC, for the hours shown.
 * @property fetched when it was downloaded.
 * @property now the current weather.
 * @property hours hourly forecast from the current hour on.
 */
data class WeatherForecast(
    val place: Coordinates,
    val offset: ZoneOffset,
    val fetched: Instant,
    val now: WeatherReading,
    val hours: List<WeatherReading>,
    val todayLow: Double?,
    val todayHigh: Double?,
)

/**
 * The weather during a session, from the hourly values of a weather model at its start and middle.
 *
 * @property humidity relative humidity in per cent.
 * @property windSpeed mean wind 10 m above ground, in metres per second.
 * @property precipitation in millimetres over the hour.
 */
data class SessionWeather(
    val time: Instant,
    val condition: WeatherCondition,
    val temperature: Double,
    val apparentTemperature: Double,
    val dewPoint: Double,
    val humidity: Double,
    val windSpeed: Double,
    val windGusts: Double?,
    val precipitation: Double,
)
