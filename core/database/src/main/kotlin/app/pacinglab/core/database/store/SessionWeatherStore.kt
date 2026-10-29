package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.SessionWeatherEntity
import app.pacinglab.core.model.SessionWeather
import app.pacinglab.core.model.WeatherCondition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What a session's weather lookup found: the weather, or that there was none to find. */
sealed interface StoredSessionWeather {
    data class Found(val weather: SessionWeather) : StoredSessionWeather

    /** Looked up at [fetched] without a result (no route, or no data for the place and time). */
    data class Missing(val fetched: Instant) : StoredSessionWeather
}

/** Keeps the weather of sessions once looked up, by the session's first workout id. */
@Singleton
class SessionWeatherStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.sessionWeatherDao()

    fun observe(workoutId: String): Flow<StoredSessionWeather?> = dao.observe(workoutId).map { it?.toDomain() }

    suspend fun get(workoutId: String): StoredSessionWeather? = dao.get(workoutId)?.toDomain()

    suspend fun save(workoutId: String, weather: SessionWeather, fetched: Instant) = dao.upsert(
        SessionWeatherEntity(
            workoutId = workoutId,
            fetchedMs = fetched.toEpochMilli(),
            timeMs = weather.time.toEpochMilli(),
            condition = weather.condition.name,
            temperature = weather.temperature,
            apparentTemperature = weather.apparentTemperature,
            dewPoint = weather.dewPoint,
            humidity = weather.humidity,
            windSpeed = weather.windSpeed,
            windGusts = weather.windGusts,
            precipitation = weather.precipitation,
        ),
    )

    suspend fun saveMissing(workoutId: String, fetched: Instant) = dao.upsert(
        SessionWeatherEntity(workoutId, fetched.toEpochMilli(), fetched.toEpochMilli(), null, null, null, null, null, null, null, null),
    )

    suspend fun clear() = dao.clear()

    private fun SessionWeatherEntity.toDomain(): StoredSessionWeather {
        val temperature = temperature ?: return StoredSessionWeather.Missing(Instant.ofEpochMilli(fetchedMs))
        return StoredSessionWeather.Found(
            SessionWeather(
                time = Instant.ofEpochMilli(timeMs),
                condition = WeatherCondition.entries.firstOrNull { it.name == condition } ?: WeatherCondition.CLOUDY,
                temperature = temperature,
                apparentTemperature = apparentTemperature ?: temperature,
                dewPoint = dewPoint ?: temperature,
                humidity = humidity ?: 0.0,
                windSpeed = windSpeed ?: 0.0,
                windGusts = windGusts,
                precipitation = precipitation ?: 0.0,
            ),
        )
    }
}
