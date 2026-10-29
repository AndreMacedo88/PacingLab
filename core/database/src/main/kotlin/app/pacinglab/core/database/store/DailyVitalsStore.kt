package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.DailyVitalsEntity
import app.pacinglab.core.model.DailyVitals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the watch app's nightly values from the Google Health API, one row per morning. */
@Singleton
class DailyVitalsStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.dailyVitalsDao()

    fun observe(): Flow<List<DailyVitals>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** The newest date stored, or null when nothing is. */
    suspend fun latest(): LocalDate? = dao.latest()?.let(LocalDate::ofEpochDay)

    /** The newest date with a VO2max, or null when there is none. */
    suspend fun latestVo2Max(): LocalDate? = dao.latestVo2Max()?.let(LocalDate::ofEpochDay)

    /** Saves [vitals]; a value missing from an entry keeps the one stored for that date. */
    suspend fun merge(vitals: List<DailyVitals>) {
        if (vitals.isEmpty()) return
        dao.merge(vitals.map { it.toEntity() }) { new, old -> new.toDomain().orElse(old.toDomain()).toEntity() }
    }

    /** Removes everything, for when the athlete disconnects the source. */
    suspend fun clear() = dao.clear()
}

private fun DailyVitalsEntity.toDomain() = DailyVitals(
    date = LocalDate.ofEpochDay(epochDay),
    hrvMs = hrvMs,
    restingHr = restingHr,
    breathingRate = breathingRate,
    oxygenSaturation = oxygenSaturation,
    oxygenSaturationLow = oxygenSaturationLow,
    oxygenSaturationHigh = oxygenSaturationHigh,
    skinTemperature = skinTemperature,
    skinTemperatureBaseline = skinTemperatureBaseline,
    vo2max = vo2max,
    vo2maxVariance = vo2maxVariance,
)

private fun DailyVitals.toEntity() = DailyVitalsEntity(
    epochDay = date.toEpochDay(),
    hrvMs = hrvMs,
    restingHr = restingHr,
    breathingRate = breathingRate,
    oxygenSaturation = oxygenSaturation,
    oxygenSaturationLow = oxygenSaturationLow,
    oxygenSaturationHigh = oxygenSaturationHigh,
    skinTemperature = skinTemperature,
    skinTemperatureBaseline = skinTemperatureBaseline,
    vo2max = vo2max,
    vo2maxVariance = vo2maxVariance,
)
