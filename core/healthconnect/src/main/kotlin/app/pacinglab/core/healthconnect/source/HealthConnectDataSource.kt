package app.pacinglab.core.healthconnect.source

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import app.pacinglab.core.healthconnect.timingOf
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.source.ChangeBatch
import app.pacinglab.core.model.source.ChangeChannel
import app.pacinglab.core.model.source.HealthDataSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * [HealthDataSource] backed by Health Connect.
 *
 * Streams are read for groups of nearby workouts rather than per workout, to stay within Health
 * Connect's rate limits. Each workout takes its streams from the app that recorded it where possible:
 * other apps (Google Fit, the phone itself) write overlapping distance and step records that would
 * otherwise be double counted. Routes arrive with the session records themselves.
 */
@Singleton
class HealthConnectDataSource @Inject constructor(private val gateway: HealthConnectGateway, private val clock: Clock) : HealthDataSource {
    override suspend fun availableChannels(): Set<ChangeChannel> {
        val granted = gateway.grantedPermissions()
        return ChangeChannel.entries.filterTo(mutableSetOf()) { HealthPermission.getReadPermission(recordType(it)) in granted }
    }

    override suspend fun earliestWorkoutTime(): Instant? = gateway
        .readPage(ExerciseSessionRecord::class, Instant.EPOCH, clock.instant().plus(LONGEST_SESSION), pageSize = 1)
        .records
        .firstOrNull()
        ?.startTime

    override suspend fun readWorkouts(from: Instant, to: Instant): List<SourceWorkout> =
        readSessions(from, to).map(ExerciseSessionRecord::toSourceWorkout)

    override suspend fun readStreams(workouts: List<SourceWorkout>): Map<String, WorkoutStreams> {
        val result = mutableMapOf<String, WorkoutStreams>()
        for (window in readWindows(workouts)) {
            val from = window.minOf { it.start }.minus(STREAM_PADDING)
            val to = window.maxOf { it.end }.plus(STREAM_PADDING)
            val heartRate = readAll(HeartRateRecord::class, from, to)
            val speed = readAll(SpeedRecord::class, from, to)
            val distance = readAll(DistanceRecord::class, from, to)
            val steps = readAll(StepsRecord::class, from, to)
            for (workout in window) {
                val hrSamples = heartRate.byOrigin { r -> r.samples.map { it.time to it.beatsPerMinute } }
                val speedSamples = speed.byOrigin { r -> r.samples.map { it.time to it.speed.inMetersPerSecond } }
                result[workout.sourceRecordId] = WorkoutStreams(
                    heartRate = heartRateSeries(pickOrigin(hrSamples, workout), workout.start, workout.end),
                    speed = sampleSeries(pickOrigin(speedSamples, workout), workout.start, workout.end),
                    route = workout.route ?: Route.EMPTY,
                    reportedDistanceMeters = distance
                        .filter { it.metadata.dataOrigin.packageName == workout.origin }
                        .sumOf { proportionalShare(it.distance.inMeters, it.startTime, it.endTime, workout.start, workout.end) }
                        .takeIf { it > 0 },
                    steps = cumulativeSteps(
                        steps.filter {
                            it.metadata.dataOrigin.packageName == workout.origin
                        },
                        workout.start,
                        workout.end,
                    ),
                )
            }
        }
        return result
    }

    override suspend fun readMeasurements(kind: MeasurementKind, from: Instant, to: Instant): List<Measurement> =
        readAll(recordType(ChangeChannel.forMeasurement(kind)), from, to).mapNotNull { it.toMeasurement() }

    override suspend fun readSleep(from: Instant, to: Instant): List<SleepSession> =
        readAll(SleepSessionRecord::class, from, to.plus(LONGEST_SESSION))
            .filter { it.startTime >= from && it.startTime < to }
            .map(SleepSessionRecord::toSleepSession)

    override suspend fun readHeartRate(from: Instant, to: Instant, origin: String): HeartRateSeries = heartRateSeries(
        // Records may start before the span; their later samples still count.
        readAll(HeartRateRecord::class, from.minus(STREAM_PADDING), to)
            .filter { it.metadata.dataOrigin.packageName == origin }
            .flatMap { record -> record.samples.map { it.time to it.beatsPerMinute } },
        from,
        to,
    )

    override suspend fun readHeartRate(from: Instant, to: Instant): HeartRateSeries {
        val byOrigin = readAll(HeartRateRecord::class, from.minus(STREAM_PADDING), to).byOrigin { record ->
            record.samples.map { it.time to it.beatsPerMinute }
        }
        val samples = byOrigin.values.maxByOrNull { it.size } ?: return HeartRateSeries.EMPTY
        return heartRateSeries(samples, from, to)
    }

    override suspend fun changesToken(channel: ChangeChannel): String = gateway.changesToken(recordType(channel))

    override suspend fun readChanges(channel: ChangeChannel, token: String): ChangeBatch {
        val response = gateway.changes(token)
        if (response.tokenExpired) {
            return ChangeBatch(nextToken = token, hasMore = false, tokenExpired = true)
        }
        val upserted = response.upserted
        // Health Connect reports upserts and deletions in separate lists, so their order is lost.
        // Upserted records are read as they are now, which means they exist: a deletion of the same
        // id came before a rewrite. Fitbit rewrites workouts this way (record ids derive from its
        // client ids, so the rewrite keeps the id), and applying such a deletion would lose the workout.
        val upsertedIds = upserted.mapTo(HashSet()) { it.metadata.id }
        val deleted = response.deletedIds.filterNot { it in upsertedIds }
        return when (channel) {
            ChangeChannel.WORKOUTS -> ChangeBatch(
                workouts = upserted.filterIsInstance<ExerciseSessionRecord>().map { it.toSourceWorkout() },
                deletedRecordIds = deleted,
                nextToken = response.nextToken,
                hasMore = response.hasMore,
            )

            ChangeChannel.HEART_RATE, ChangeChannel.SPEED, ChangeChannel.DISTANCE, ChangeChannel.STEPS -> ChangeBatch(
                affectedSpans = upserted.map { record -> timingOf(record).let { it.start..(it.end ?: it.start) } },
                deletedRecordIds = deleted,
                nextToken = response.nextToken,
                hasMore = response.hasMore,
            )

            ChangeChannel.SLEEP -> ChangeBatch(
                sleep = upserted.filterIsInstance<SleepSessionRecord>().map { it.toSleepSession() },
                deletedRecordIds = deleted,
                nextToken = response.nextToken,
                hasMore = response.hasMore,
            )

            else -> ChangeBatch(
                measurements = upserted.mapNotNull { it.toMeasurement() },
                deletedRecordIds = deleted,
                nextToken = response.nextToken,
                hasMore = response.hasMore,
            )
        }
    }

    /** Sessions starting within [from, to). Health Connect filters intervals on both ends, so widen. */
    private suspend fun readSessions(from: Instant, to: Instant): List<ExerciseSessionRecord> =
        readAll(ExerciseSessionRecord::class, from, to.plus(LONGEST_SESSION))
            .filter { it.startTime >= from && it.startTime < to }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant, origins: Set<String> = emptySet()): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val page = gateway.readPage(type, from, to, origins, pageToken = pageToken)
            records += page.records
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return records
    }

    /**
     * Groups workouts so each group needs one read per stream type: workouts close together always
     * share a read, and groups grow up to [MAX_READ_SPAN] to keep the number of calls low.
     */
    private fun readWindows(workouts: List<SourceWorkout>): List<List<SourceWorkout>> {
        val windows = mutableListOf<MutableList<SourceWorkout>>()
        for (workout in workouts.sortedBy { it.start }) {
            val current = windows.lastOrNull()
            if (current != null && Duration.between(current.first().start, workout.end) <= MAX_READ_SPAN) {
                current += workout
            } else {
                windows += mutableListOf(workout)
            }
        }
        return windows
    }

    /** Stream samples per recording app, for the records' own time span. */
    private inline fun <R : Record, V> List<R>.byOrigin(samples: (R) -> List<Pair<Instant, V>>): Map<String, List<Pair<Instant, V>>> =
        groupBy { it.metadata.dataOrigin.packageName }.mapValues { (_, records) -> records.flatMap(samples) }

    /**
     * Samples from the app that recorded the workout. If that app wrote none (Google Fit detects walks
     * and rides but records no heart rate), the app with the most samples during the workout is used.
     */
    private fun <V> pickOrigin(byOrigin: Map<String, List<Pair<Instant, V>>>, workout: SourceWorkout): List<Pair<Instant, V>> {
        fun inside(samples: List<Pair<Instant, V>>) = samples.count { (time, _) -> time >= workout.start && time <= workout.end }
        val own = byOrigin[workout.origin].orEmpty()
        if (inside(own) > 0) return own
        return byOrigin.values.maxByOrNull(::inside)?.takeIf { inside(it) > 0 }.orEmpty()
    }

    /**
     * The step count of the records within [from, to] as a running total: a point where each record starts
     * (when it does not start where the previous one ended) and one where it ends. Steps of records that
     * reach outside the span are counted in proportion to their time inside it.
     */
    private fun cumulativeSteps(records: List<StepsRecord>, from: Instant, to: Instant): SampleSeries {
        val times = mutableListOf<Long>()
        val totals = mutableListOf<Double>()
        var total = 0.0
        for (record in records.sortedBy { it.startTime }) {
            val start = maxOf(record.startTime, from)
            val end = minOf(record.endTime, to)
            if (end <= start || (times.isNotEmpty() && start.toEpochMilli() < times.last())) continue
            if (times.isEmpty() || start.toEpochMilli() > times.last()) {
                times += start.toEpochMilli()
                totals += total
            }
            total += proportionalShare(record.count.toDouble(), record.startTime, record.endTime, from, to)
            times += end.toEpochMilli()
            totals += total
        }
        return if (total > 0) SampleSeries(times.toLongArray(), totals.toDoubleArray()) else SampleSeries.EMPTY
    }

    private fun recordType(channel: ChangeChannel): KClass<out Record> = when (channel) {
        ChangeChannel.WORKOUTS -> ExerciseSessionRecord::class
        ChangeChannel.HEART_RATE -> HeartRateRecord::class
        ChangeChannel.SPEED -> SpeedRecord::class
        ChangeChannel.DISTANCE -> DistanceRecord::class
        ChangeChannel.STEPS -> StepsRecord::class
        ChangeChannel.SLEEP -> SleepSessionRecord::class
        ChangeChannel.RESTING_HEART_RATE -> RestingHeartRateRecord::class
        ChangeChannel.HRV -> HeartRateVariabilityRmssdRecord::class
        ChangeChannel.VO2_MAX -> Vo2MaxRecord::class
        ChangeChannel.WEIGHT -> WeightRecord::class
        ChangeChannel.RESPIRATORY_RATE -> RespiratoryRateRecord::class
        ChangeChannel.OXYGEN_SATURATION -> OxygenSaturationRecord::class
    }

    private companion object {
        /** Stream records are one minute long and can start before the session; read a margin around it. */
        val STREAM_PADDING: Duration = Duration.ofMinutes(2)

        /** Longest span covered by one stream read. Two days of all-day heart rate fit in one page. */
        val MAX_READ_SPAN: Duration = Duration.ofDays(2)

        /** Upper bound on a session's length, used to widen interval queries. */
        val LONGEST_SESSION: Duration = Duration.ofDays(1)
    }
}
