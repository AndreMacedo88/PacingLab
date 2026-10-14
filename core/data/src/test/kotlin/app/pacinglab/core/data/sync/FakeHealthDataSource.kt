package app.pacinglab.core.data.sync

import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.source.ChangeBatch
import app.pacinglab.core.model.source.ChangeChannel
import app.pacinglab.core.model.source.HealthDataSource
import app.pacinglab.core.model.source.RateLimitedException
import java.io.IOException
import java.time.Instant

/** In-memory [HealthDataSource] with a change log, failure injection and token expiry. */
class FakeHealthDataSource : HealthDataSource {
    val workouts = mutableMapOf<String, SourceWorkout>()
    val streams = mutableMapOf<String, WorkoutStreams>()
    val measurements = mutableMapOf<String, Measurement>()
    var channels: Set<ChangeChannel> = ChangeChannel.entries.toSet()

    /** readWorkouts fails for windows starting before this instant, once per assignment. */
    var failReadsBefore: Instant? = null

    /** Next readChanges call reports an expired token. */
    var expireNextToken = false

    /** The next call of any kind is refused with a rate limit. */
    var rateLimitNextCall = false

    /** Whether routes are delivered; Health Connect withholds them from apps in the background. */
    var routesAvailable = true

    var workoutReads = 0
        private set

    private val log = mutableListOf<Pair<ChangeChannel, Any>>()

    fun addWorkout(workout: SourceWorkout, workoutStreams: WorkoutStreams = WorkoutStreams.EMPTY) {
        workouts[workout.sourceRecordId] = workout
        streams[workout.sourceRecordId] = workoutStreams
        log += ChangeChannel.WORKOUTS to workout
    }

    fun deleteWorkout(id: String) {
        workouts.remove(id)
        streams.remove(id)
        log += ChangeChannel.WORKOUTS to Deleted(id)
    }

    fun replaceStreams(id: String, workoutStreams: WorkoutStreams) {
        streams[id] = workoutStreams
        val workout = workouts.getValue(id)
        log += ChangeChannel.HEART_RATE to (workout.start..workout.end)
    }

    fun addMeasurement(measurement: Measurement) {
        measurements[measurement.sourceRecordId] = measurement
        log += ChangeChannel.forMeasurement(measurement.kind) to measurement
    }

    /** Changes Health Connect would hold without recording them in the log (lost to token expiry). */
    fun deleteWorkoutSilently(id: String) {
        workouts.remove(id)
        streams.remove(id)
    }

    override suspend fun availableChannels(): Set<ChangeChannel> = channels

    override suspend fun earliestWorkoutTime(): Instant? = workouts.values.minOfOrNull { it.start }

    override suspend fun readWorkouts(from: Instant, to: Instant): List<SourceWorkout> {
        checkRateLimit()
        workoutReads++
        failReadsBefore?.let { limit ->
            if (from < limit) {
                failReadsBefore = null
                throw IOException("Simulated rate limit")
            }
        }
        return workouts.values
            .filter { it.start >= from && it.start < to }
            .map { if (routesAvailable) it else it.copy(route = null) }
    }

    override suspend fun readStreams(workouts: List<SourceWorkout>): Map<String, WorkoutStreams> =
        workouts.associate { it.sourceRecordId to (streams[it.sourceRecordId] ?: WorkoutStreams.EMPTY) }

    override suspend fun readMeasurements(kind: MeasurementKind, from: Instant, to: Instant): List<Measurement> =
        measurements.values.filter { it.kind == kind && it.time >= from && it.time < to }

    override suspend fun readSleep(from: Instant, to: Instant): List<SleepSession> = emptyList()

    override suspend fun changesToken(channel: ChangeChannel): String = log.size.toString()

    override suspend fun readChanges(channel: ChangeChannel, token: String): ChangeBatch {
        checkRateLimit()
        if (expireNextToken) {
            expireNextToken = false
            return ChangeBatch(nextToken = token, hasMore = false, tokenExpired = true)
        }
        val entries = log.drop(token.toInt()).filter { it.first == channel }.map { it.second }
        return ChangeBatch(
            workouts = entries.filterIsInstance<SourceWorkout>().map { if (routesAvailable) it else it.copy(route = null) },
            measurements = entries.filterIsInstance<Measurement>(),
            affectedSpans = entries.filterIsInstance<ClosedRange<*>>().map {
                @Suppress("UNCHECKED_CAST")
                it as ClosedRange<Instant>
            },
            deletedRecordIds = entries.filterIsInstance<Deleted>().map { it.id },
            nextToken = log.size.toString(),
            hasMore = false,
        )
    }

    private fun checkRateLimit() {
        if (rateLimitNextCall) {
            rateLimitNextCall = false
            throw RateLimitedException("Simulated rate limit")
        }
    }

    private data class Deleted(val id: String)
}
