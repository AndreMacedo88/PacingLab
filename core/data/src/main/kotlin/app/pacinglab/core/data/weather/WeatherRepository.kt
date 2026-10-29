package app.pacinglab.core.data.weather

import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.WeatherForecast
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

sealed interface WeatherState {
    /** The athlete has not allowed approximate location. */
    data object NeedsLocation : WeatherState

    /** Location is switched off or unknown, and no recent forecast is kept. */
    data object NoLocation : WeatherState

    /** The forecast could not be downloaded, and no recent one is kept. */
    data object NoConnection : WeatherState

    /** @property outdated the forecast is an older one, because a new one could not be downloaded. */
    data class Ready(val forecast: WeatherForecast, val outdated: Boolean = false) : WeatherState
}

/**
 * The weather where the phone is, from Open-Meteo. Only the approximate position, rounded to two
 * decimals (about 1 km), leaves the phone. A forecast is reused for [FRESH] while the phone stays
 * within a few kilometres, and an older one is shown for up to [KEPT] when there is no connection.
 */
@Singleton
class WeatherRepository @Inject constructor(
    private val service: WeatherService,
    private val location: ApproximateLocation,
    private val cache: WeatherCache,
    private val clock: Clock,
) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutex = Mutex()
    private var last: WeatherForecast? = null

    /** The weather now, checked again every few minutes and on [refresh] while collected. */
    fun observe(): Flow<WeatherState> = merge(
        flowOf(Unit),
        flow {
            while (true) {
                delay(CHECK_MILLIS)
                emit(Unit)
            }
        },
        requests,
    ).conflate().map { load() }

    /** Checks again, for instance once the athlete allowed location or pulled to refresh. */
    fun refresh() {
        requests.tryEmit(Unit)
    }

    suspend fun load(): WeatherState = mutex.withLock {
        if (!location.permitted()) return WeatherState.NeedsLocation
        val now = clock.instant()
        val kept = (last ?: cached())?.takeIf { Duration.between(it.fetched, now) < KEPT }
        val place = location.current()?.rounded()
            ?: return kept?.let { WeatherState.Ready(it.from(now), outdated = true) } ?: WeatherState.NoLocation
        val nearby = kept?.takeIf { it.place.near(place) }
        if (nearby != null && Duration.between(nearby.fetched, now) < FRESH) return WeatherState.Ready(nearby.from(now))
        return try {
            val text = service.fetch(place)
            val forecast = OpenMeteo.parse(text, place, now)
            last = forecast
            cache.write(
                JSONObject().put(
                    "latitude",
                    place.latitude,
                ).put("longitude", place.longitude).put("fetched", now.toString()).put("response", text).toString(),
            )
            WeatherState.Ready(forecast)
        } catch (_: IOException) {
            nearby?.let { WeatherState.Ready(it.from(now), outdated = true) } ?: WeatherState.NoConnection
        } catch (_: JSONException) {
            nearby?.let { WeatherState.Ready(it.from(now), outdated = true) } ?: WeatherState.NoConnection
        }
    }

    private suspend fun cached(): WeatherForecast? = cache.read()?.let { text ->
        try {
            val json = JSONObject(text)
            OpenMeteo.parse(
                json.getString("response"),
                Coordinates(json.getDouble("latitude"), json.getDouble("longitude")),
                Instant.parse(json.getString("fetched")),
            ).also { last = it }
        } catch (_: RuntimeException) {
            null
        } catch (_: JSONException) {
            null
        }
    }

    /** The forecast from the current hour on. */
    private fun WeatherForecast.from(now: Instant): WeatherForecast {
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        return copy(hours = hours.filter { it.time >= hour })
    }

    private fun Coordinates.rounded() = Coordinates((latitude * 100).roundToInt() / 100.0, (longitude * 100).roundToInt() / 100.0)

    /** Within [NEARBY_KM], on a flat-earth approximation that is plenty at this distance. */
    private fun Coordinates.near(other: Coordinates): Boolean {
        val north = (latitude - other.latitude) * KM_PER_DEGREE
        val east = (longitude - other.longitude) * KM_PER_DEGREE * cos(Math.toRadians(latitude))
        return abs(north) < NEARBY_KM && abs(east) < NEARBY_KM
    }

    companion object {
        val FRESH: Duration = Duration.ofMinutes(20)
        val KEPT: Duration = Duration.ofHours(12)
        private const val CHECK_MILLIS = 10 * 60_000L
        private const val NEARBY_KM = 5.0
        private const val KM_PER_DEGREE = 111.2
    }
}
