package app.pacinglab.core.data.strain

import app.pacinglab.core.analytics.strain.StillMinutes
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.StillHeartRate
import app.pacinglab.core.model.source.HealthDataSource
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A day's still heart rate ([StillMinutes]): heart rate and steps read from the data source, sleep and
 * workouts from the database. Throws what the source throws, such as a rate limit.
 */
@Singleton
class StillHeartRateReader @Inject constructor(
    private val source: HealthDataSource,
    private val measurements: MeasurementStore,
    private val workouts: WorkoutStore,
    private val clock: Clock,
) {
    /** The still heart rate of [date] in the phone's time zone, up to [until] for today. */
    suspend fun read(date: LocalDate, until: Instant? = null): StillHeartRate {
        val zone = clock.zone
        val from = date.atStartOfDay(zone).toInstant()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().let { if (until != null && until < it) until else it }
        val heartRate = source.readHeartRate(from, to)
        val steps = source.readSteps(from.minus(Duration.ofMinutes(StillMinutes.SETTLE_MINUTES.toLong())), to)
        val sleeps = measurements.sleepOverlapping(from, to)
        val nearby = workouts.observeAll().first().filter { it.end > from.minus(StillMinutes.AFTER_TRAINING) && it.start < to }
        return StillMinutes.summarize(date, zone, heartRate, steps, StillMinutes.excludedSpans(sleeps, nearby), until)
    }
}
