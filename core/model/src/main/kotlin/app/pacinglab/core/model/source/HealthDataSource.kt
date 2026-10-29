package app.pacinglab.core.model.source

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.WorkoutStreams
import java.time.Instant

/**
 * Record types whose changes are tracked, each with its own change token so that a revoked
 * permission for one type does not break the others.
 */
enum class ChangeChannel(val measurementKind: MeasurementKind? = null) {
    WORKOUTS,
    HEART_RATE,
    SPEED,
    DISTANCE,
    STEPS,
    SLEEP,
    RESTING_HEART_RATE(MeasurementKind.RESTING_HEART_RATE),
    HRV(MeasurementKind.HRV_RMSSD),
    VO2_MAX(MeasurementKind.VO2_MAX),
    WEIGHT(MeasurementKind.WEIGHT),
    RESPIRATORY_RATE(MeasurementKind.RESPIRATORY_RATE),
    OXYGEN_SATURATION(MeasurementKind.OXYGEN_SATURATION),
    ;

    companion object {
        fun forMeasurement(kind: MeasurementKind): ChangeChannel = entries.first { it.measurementKind == kind }
    }
}

/**
 * One page of changes from a source.
 *
 * @property affectedSpans time spans of changed stream records (heart rate, speed, distance, steps);
 *   workouts overlapping them need their streams read again.
 * @property deletedRecordIds source record ids that no longer exist, of any record type in the channel.
 *   Never names a record that the same batch delivers as new or changed.
 * @property tokenExpired the token was too old; the caller must re-read recent data and start over
 *   with a fresh token.
 */
data class ChangeBatch(
    val workouts: List<SourceWorkout> = emptyList(),
    val measurements: List<Measurement> = emptyList(),
    val sleep: List<SleepSession> = emptyList(),
    val affectedSpans: List<ClosedRange<Instant>> = emptyList(),
    val deletedRecordIds: List<String> = emptyList(),
    val nextToken: String,
    val hasMore: Boolean,
    val tokenExpired: Boolean = false,
)

/**
 * The source refused a call because the app made too many. Nothing is wrong with the data; wait and
 * try again later.
 */
class RateLimitedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A provider of health data, such as Health Connect. Any call may throw [RateLimitedException]. */
interface HealthDataSource {
    /** Channels the user has granted access to. Reading any other channel fails. */
    suspend fun availableChannels(): Set<ChangeChannel>

    /** Earliest time the source can return data for, or null if it holds no workouts. */
    suspend fun earliestWorkoutTime(): Instant?

    /** Workouts starting within [from, to), with their routes when the source can provide them. */
    suspend fun readWorkouts(from: Instant, to: Instant): List<SourceWorkout>

    /** Streams for each workout, keyed by [SourceWorkout.sourceRecordId]. Reads in bulk where it can. */
    suspend fun readStreams(workouts: List<SourceWorkout>): Map<String, WorkoutStreams>

    suspend fun readMeasurements(kind: MeasurementKind, from: Instant, to: Instant): List<Measurement>

    suspend fun readSleep(from: Instant, to: Instant): List<SleepSession>

    /** Heart rate that [origin] recorded within [from, to], such as during a night's sleep. */
    suspend fun readHeartRate(from: Instant, to: Instant, origin: String): HeartRateSeries

    /**
     * Heart rate within [from, to] from the app that recorded most of it, normally the watch's: the
     * all-day heart rate of a day, read on demand rather than stored.
     */
    suspend fun readHeartRate(from: Instant, to: Instant): HeartRateSeries

    /** A token marking "now" for [channel], to be passed to [readChanges] later. */
    suspend fun changesToken(channel: ChangeChannel): String

    suspend fun readChanges(channel: ChangeChannel, token: String): ChangeBatch
}
