package app.pacinglab.core.data.weather

import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.database.store.SessionWeatherStore
import app.pacinglab.core.database.store.StoredSessionWeather
import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.SessionWeather
import app.pacinglab.core.model.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** What is known about a session's weather. */
sealed interface SessionWeatherState {
    /** The athlete switched weather for sessions off. */
    data object Off : SessionWeatherState

    /** The session has no route, so no place to look the weather up for. */
    data object NoPlace : SessionWeatherState

    /** The weather service had nothing for the place and time. */
    data object Missing : SessionWeatherState

    /** The service could not be reached; opening the session again retries. */
    data object Unreachable : SessionWeatherState

    data class Found(val weather: SessionWeather) : SessionWeatherState
}

/** Hourly weather of the past from Open-Meteo. */
interface PastWeatherService {
    /** Open-Meteo's hourly response for [place] over the UTC days [from] to [to]; throws [IOException] without a connection. */
    suspend fun fetch(place: Coordinates, from: LocalDate, to: LocalDate, recent: Boolean): String
}

/**
 * The weather of a session, looked up once from Open-Meteo by the place it started and kept in the
 * database. Only the start of the route is sent, rounded to two decimals (about 1 km), with the date;
 * nothing is looked up for sessions without a route or while the setting is off.
 */
@Singleton
class SessionWeatherRepository @Inject constructor(
    private val store: SessionWeatherStore,
    private val service: PastWeatherService,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    /** The weather of the session that starts with workout [workoutId], at [place], from [start] to [end]. */
    suspend fun weatherOf(workoutId: String, place: Coordinates?, start: Instant, end: Instant): SessionWeatherState {
        when (val stored = store.get(workoutId)) {
            is StoredSessionWeather.Found -> return SessionWeatherState.Found(stored.weather)
            is StoredSessionWeather.Missing -> return SessionWeatherState.Missing
            null -> Unit
        }
        if (!preferences.preferences.first().sessionWeather) return SessionWeatherState.Off
        val rounded = place?.let { Coordinates(round(it.latitude), round(it.longitude)) } ?: return SessionWeatherState.NoPlace
        val middle = Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2)
        val recent = start.atOffset(ZoneOffset.UTC).toLocalDate() >= LocalDate.now(clock).minusDays(RECENT_DAYS)
        val text = try {
            service.fetch(rounded, start.atOffset(ZoneOffset.UTC).toLocalDate(), end.atOffset(ZoneOffset.UTC).toLocalDate(), recent)
        } catch (_: IOException) {
            return SessionWeatherState.Unreachable
        }
        val weather = try {
            OpenMeteoPast.at(text, middle)
        } catch (_: JSONException) {
            null
        }
        return if (weather == null) {
            store.saveMissing(workoutId, clock.instant())
            SessionWeatherState.Missing
        } else {
            store.save(workoutId, weather, clock.instant())
            SessionWeatherState.Found(weather)
        }
    }

    private fun round(degrees: Double) = (degrees * 100).roundToInt() / 100.0

    companion object {
        /** Days the forecast service keeps (it serves about three months back); older days come from the archive. */
        const val RECENT_DAYS = 80L
    }
}

/** Reads Open-Meteo's hourly values of the past. */
internal object OpenMeteoPast {
    const val HOURLY = "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,wind_speed_10m,wind_gusts_10m," +
        "precipitation,weather_code"

    /**
     * The forecast service for the last months (higher resolution, with DMI's model in Denmark), the
     * archive (ERA5 reanalysis blended with the ECMWF model) for anything older.
     */
    fun url(place: Coordinates, from: LocalDate, to: LocalDate, recent: Boolean): String = String.format(
        Locale.ROOT,
        "https://%s?latitude=%.2f&longitude=%.2f&start_date=%s&end_date=%s&hourly=%s&wind_speed_unit=ms&timeformat=unixtime",
        if (recent) "api.open-meteo.com/v1/forecast" else "archive-api.open-meteo.com/v1/archive",
        place.latitude,
        place.longitude,
        from,
        to,
        HOURLY,
    )

    /**
     * The weather at [time] from the hourly values in [text], interpolated between the hours around it;
     * precipitation and the sky are those of the hour it falls in. Null when [time] is outside the hours
     * or the values there are missing.
     */
    fun at(text: String, time: Instant): SessionWeather? {
        val hourly = JSONObject(text).getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val second = time.epochSecond
        val after = (0 until times.length()).firstOrNull { times.getLong(it) >= second } ?: return null
        val before = if (times.getLong(after) == second || after == 0) after else after - 1
        val t0 = times.getLong(before)
        val t1 = times.getLong(after)
        val share = if (t1 == t0) 0.0 else (second - t0).toDouble() / (t1 - t0)
        fun value(name: String, i: Int): Double? = (hourly.optJSONArray(name)?.opt(i) as? Number)?.toDouble()
        fun interpolated(name: String): Double? {
            val a = value(name, before) ?: return null
            val b = value(name, after) ?: return null
            return a + (b - a) * share
        }
        val temperature = interpolated("temperature_2m") ?: return null
        return SessionWeather(
            time = time,
            condition = value("weather_code", after)?.toInt()?.let(WeatherCondition::ofWmo) ?: WeatherCondition.CLOUDY,
            temperature = temperature,
            apparentTemperature = interpolated("apparent_temperature") ?: temperature,
            dewPoint = interpolated("dew_point_2m") ?: temperature,
            humidity = interpolated("relative_humidity_2m") ?: return null,
            windSpeed = interpolated("wind_speed_10m") ?: 0.0,
            windGusts = interpolated("wind_gusts_10m"),
            precipitation = value("precipitation", after) ?: 0.0,
        )
    }
}

class OpenMeteoPastService @Inject constructor() : PastWeatherService {
    override suspend fun fetch(place: Coordinates, from: LocalDate, to: LocalDate, recent: Boolean): String = withContext(Dispatchers.IO) {
        val connection = URL(OpenMeteoPast.url(place, from, to, recent)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Open-Meteo answered ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}
