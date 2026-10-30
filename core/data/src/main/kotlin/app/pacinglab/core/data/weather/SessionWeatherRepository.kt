package app.pacinglab.core.data.weather

import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.database.store.SessionWeatherStore
import app.pacinglab.core.database.store.StoredSessionWeather
import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.SessionWeather
import app.pacinglab.core.model.WeatherCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** A run for the weather lookup: its time and where its route started, if it has one. */
data class RunPlace(val workoutId: String, val start: Instant, val end: Instant, val place: Coordinates?)

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

    /** The weather found for every workout that has some, by workout id. */
    fun observeFound(): Flow<Map<String, SessionWeather>> = store.observeFound()

    /** Workouts whose weather was looked up already, with or without a result. */
    suspend fun lookedUp(): Set<String> = store.lookedUp()

    /**
     * Looks up the weather of every run in [runs] that has none yet, for the heat adjustment of heart rate
     * based estimates. A run without a route takes the place of a route recorded within [NEARBY] of it
     * (the warm-up before an interval workout); runs from one rounded place share one request over their
     * dates, so a year of runs from home costs one or two requests. At most [MAX_REQUESTS] a call; the
     * rest follow with later syncs.
     */
    suspend fun fillRuns(runs: List<RunPlace>) {
        if (!preferences.preferences.first().sessionWeather) return
        val done = store.lookedUp()
        val withRoute = runs.filter { it.place != null }
        val missing = runs.filter { it.workoutId !in done }.mapNotNull { run ->
            val place = run.place ?: withRoute.firstOrNull { other ->
                Duration.between(other.start, run.start).abs() <= NEARBY || Duration.between(other.end, run.start).abs() <= NEARBY
            }?.place
            place?.let { run to Coordinates(round(it.latitude), round(it.longitude)) }
        }
        val today = LocalDate.now(clock)
        // One request per place and kind of service, at most a year each, to keep responses small.
        val groups = missing.groupBy { (run, place) ->
            val date = run.start.atOffset(ZoneOffset.UTC).toLocalDate()
            Triple(place, date >= today.minusDays(RECENT_DAYS), date.year)
        }
        for ((key, group) in groups.entries.take(MAX_REQUESTS)) {
            val (place, recent) = key.first to key.second
            val dates = group.flatMap { (run, _) -> listOf(run.start, run.end) }.map { it.atOffset(ZoneOffset.UTC).toLocalDate() }
            val hours = try {
                OpenMeteoPast.hours(service.fetch(place, dates.min(), dates.max(), recent))
            } catch (_: IOException) {
                return
            } catch (_: JSONException) {
                continue
            }
            for ((run, _) in group) {
                val middle = Instant.ofEpochMilli((run.start.toEpochMilli() + run.end.toEpochMilli()) / 2)
                hours.at(middle)?.let { store.save(run.workoutId, it, clock.instant()) }
                    ?: store.saveMissing(run.workoutId, clock.instant())
            }
        }
    }

    private fun round(degrees: Double) = (degrees * 100).roundToInt() / 100.0

    companion object {
        /** A run without a route takes the place of a route recorded this close in time. */
        val NEARBY: Duration = Duration.ofHours(3)

        /** Requests per call of [fillRuns]. */
        const val MAX_REQUESTS = 10

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

    /** The weather at [time] from Open-Meteo's response [text]; see [Hours.at]. */
    fun at(text: String, time: Instant): SessionWeather? = hours(text).at(time)

    /** The hourly values in Open-Meteo's response [text]; throws [JSONException] when it is not one. */
    fun hours(text: String): Hours = Hours(JSONObject(text).getJSONObject("hourly"))

    class Hours(private val hourly: JSONObject) {
        private val times = hourly.getJSONArray("time").let { array -> LongArray(array.length()) { array.getLong(it) } }

        /**
         * The weather at [time], interpolated between the hours around it; precipitation and the sky are
         * those of the hour it falls in. Null when [time] is outside the hours or the values there are
         * missing.
         */
        fun at(time: Instant): SessionWeather? {
            val second = time.epochSecond
            val after = times.indexOfFirst { it >= second }.takeIf { it >= 0 } ?: return null
            val before = if (times[after] == second || after == 0) after else after - 1
            val t0 = times[before]
            val t1 = times[after]
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
