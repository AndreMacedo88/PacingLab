package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.geo.speeds
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.performance.RunVo2Max
import app.pacinglab.core.analytics.performance.Vo2MaxPoint
import app.pacinglab.core.analytics.performance.Vo2MaxSource
import app.pacinglab.core.analytics.performance.Vo2MaxTrend
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every VO2max value with its source, and the trend through them.
 *
 * @property points oldest first; PacingLab's estimates are one per day, combined from that day's runs.
 */
data class Vo2MaxHistory(val points: List<Vo2MaxPoint>, val trend: Vo2MaxTrend?) {
    companion object {
        val EMPTY = Vo2MaxHistory(emptyList(), null)
    }
}

/** Performance measures: VO2max from tests, from Fitbit and from PacingLab's own run estimates. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PerformanceRepository @Inject constructor(
    private val workouts: WorkoutStore,
    private val measurements: MeasurementStore,
    trainingLoad: TrainingLoadRepository,
    private val clock: Clock,
) {
    private class Cached(val value: Double?, val windows: Int)

    private data class Key(val workoutId: String, val lastModified: Instant, val anchors: HeartRateAnchors?)

    private val estimates = ConcurrentHashMap<Key, Cached>()

    private val history: Flow<Vo2MaxHistory> = combine(
        workouts.observeAll(),
        trainingLoad.anchors,
        measurements.observeMeasurements(MeasurementKind.VO2_MAX, Instant.EPOCH, FAR_FUTURE),
    ) { all, timeline, recorded -> Triple(all, timeline, recorded) }
        .mapLatest { (all, timeline, recorded) ->
            val runs = TrainingSessions.removeDuplicates(all.filter { it.sport == Sport.RUN && TrainingLoads.counts(it) })
            val byDay = runs.mapNotNull { workout ->
                val date = TrainingLoads.dateOf(workout, clock.zone)
                val anchors = timeline.at(date)
                val key = Key(workout.id, workout.lastModified, anchors)
                val cached = estimates[key] ?: run {
                    val streams = workouts.streams(workout.id)
                    // Runs before August 2026 came without a speed stream; their GPS track gives the same estimates.
                    val speed = if (streams.speed.size > 0) streams.speed else streams.route.speeds()
                    val estimate = anchors?.let {
                        RunVo2Max.of(streams.heartRate, speed, workout.start.toEpochMilli(), workout.end.toEpochMilli(), it)
                    }
                    Cached(estimate?.value, estimate?.windows ?: 0).also { estimates[key] = it }
                }
                cached.value?.let { Triple(date, it, cached.windows) }
            }.groupBy { it.first }
            val own = byDay.map { (date, day) ->
                val windows = day.sumOf { it.third }
                // Overlapping stretches of one day are not independent: a well-covered day counts as one estimate.
                Vo2MaxPoint(
                    date,
                    day.sumOf { it.second * it.third } / windows,
                    Vo2MaxSource.PACINGLAB,
                    weight = minOf(
                        1.0,
                        windows / FULL_WEIGHT_WINDOWS,
                    ),
                )
            }
            val other = recorded.map { m ->
                Vo2MaxPoint(
                    date = m.time.atZone(m.zoneOffset ?: clock.zone).toLocalDate(),
                    value = m.value,
                    source = if (m.origin == TEST_ORIGIN) Vo2MaxSource.TEST else Vo2MaxSource.FITBIT,
                )
            }
            val points = (own + other).sortedBy { it.date }
            Vo2MaxHistory(points, Vo2MaxTrend.of(points, LocalDate.now(clock)))
        }
        .flowOn(Dispatchers.Default)

    fun observeVo2Max(): Flow<Vo2MaxHistory> = history

    /** Records a lab or field test; a second test on the same day replaces the first. */
    suspend fun saveTest(date: LocalDate, value: Double) {
        val zone = clock.zone
        measurements.saveMeasurements(
            listOf(
                Measurement(
                    sourceRecordId = testId(date),
                    kind = MeasurementKind.VO2_MAX,
                    time = date.atTime(LocalTime.NOON).atZone(zone).toInstant(),
                    zoneOffset = date.atTime(LocalTime.NOON).atZone(zone).offset,
                    value = value,
                    origin = TEST_ORIGIN,
                ),
            ),
        )
    }

    suspend fun deleteTest(date: LocalDate) = measurements.delete(listOf(testId(date)))

    private fun testId(date: LocalDate) = "pacinglab:vo2max-test:$date"

    companion object {
        /** Origin of values the athlete entered, as opposed to those an app wrote to Health Connect. */
        const val TEST_ORIGIN = "app.pacinglab.test"

        /** Steady stretches at which a day's own estimate counts fully. */
        private const val FULL_WEIGHT_WINDOWS = 20.0

        private val FAR_FUTURE: Instant = Instant.parse("2200-01-01T00:00:00Z")
    }
}
