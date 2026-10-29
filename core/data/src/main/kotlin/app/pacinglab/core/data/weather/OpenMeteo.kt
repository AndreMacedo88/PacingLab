package app.pacinglab.core.data.weather

import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.WeatherCondition
import app.pacinglab.core.model.WeatherForecast
import app.pacinglab.core.model.WeatherReading
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * Open-Meteo's forecast API (open-meteo.com): free, no key and no account. It blends the national
 * weather services' models; in Denmark that includes DMI's HARMONIE at 2 km.
 */
internal object OpenMeteo {
    private const val HOURLY = "temperature_2m,apparent_temperature,dew_point_2m,precipitation,precipitation_probability," +
        "weather_code,wind_speed_10m,wind_gusts_10m,is_day"
    private const val CURRENT = "temperature_2m,apparent_temperature,dew_point_2m,precipitation," +
        "weather_code,wind_speed_10m,wind_gusts_10m,is_day"

    /** How many hours of forecast are kept. */
    const val HOURS = 24

    fun url(place: Coordinates): String = String.format(
        Locale.ROOT,
        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=%s&hourly=%s" +
            "&daily=temperature_2m_max,temperature_2m_min&wind_speed_unit=ms&timezone=auto&timeformat=unixtime&forecast_days=2",
        place.latitude,
        place.longitude,
        CURRENT,
        HOURLY,
    )

    /** The forecast in Open-Meteo's response [text]; throws [org.json.JSONException] when it is not one. */
    fun parse(text: String, place: Coordinates, fetched: Instant): WeatherForecast {
        val json = JSONObject(text)
        val offset = ZoneOffset.ofTotalSeconds(json.getInt("utc_offset_seconds"))
        val current = json.getJSONObject("current")
        val now = reading(Instant.ofEpochSecond(current.getLong("time"))) { name -> current.opt(name) }

        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val currentHour = now.time.epochSecond / 3600 * 3600
        val hours = (0 until times.length())
            .filter { times.getLong(it) >= currentHour }
            .take(HOURS)
            .map { i -> reading(Instant.ofEpochSecond(times.getLong(i))) { name -> hourly.optJSONArray(name)?.opt(i) } }

        val daily = json.optJSONObject("daily")
        return WeatherForecast(
            place = place,
            offset = offset,
            fetched = fetched,
            now = now,
            hours = hours,
            todayLow = daily?.optJSONArray("temperature_2m_min").first(),
            todayHigh = daily?.optJSONArray("temperature_2m_max").first(),
        )
    }

    private fun reading(time: Instant, value: (String) -> Any?): WeatherReading {
        fun number(name: String): Double? = (value(name) as? Number)?.toDouble()
        val temperature = number("temperature_2m") ?: throw org.json.JSONException("No temperature at $time")
        return WeatherReading(
            time = time,
            condition = number("weather_code")?.toInt()?.let(WeatherCondition::ofWmo) ?: WeatherCondition.CLOUDY,
            temperature = temperature,
            apparentTemperature = number("apparent_temperature") ?: temperature,
            dewPoint = number("dew_point_2m") ?: temperature,
            windSpeed = number("wind_speed_10m") ?: 0.0,
            windGusts = number("wind_gusts_10m"),
            precipitation = number("precipitation") ?: 0.0,
            precipitationChance = number("precipitation_probability")?.toInt(),
            daylight = number("is_day")?.toInt() != 0,
        )
    }

    private fun JSONArray?.first(): Double? = (this?.opt(0) as? Number)?.toDouble()
}
