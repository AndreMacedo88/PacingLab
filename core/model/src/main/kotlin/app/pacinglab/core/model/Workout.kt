package app.pacinglab.core.model

import java.time.Instant
import java.time.ZoneOffset

/** Where a record came from. */
enum class DataSource {
    HEALTH_CONNECT,
}

/** A lap as recorded by the watch (Fitbit writes automatic laps per kilometre on GPS runs). */
data class Lap(val start: Instant, val end: Instant, val lengthMeters: Double?)

enum class SegmentKind {
    RUNNING,
    PAUSE,
    OTHER,
}

/** A segment within a workout as recorded by the watch. [sourceType] keeps the source's own code. */
data class Segment(val start: Instant, val end: Instant, val kind: SegmentKind, val sourceType: Int, val repetitions: Int)

/**
 * A workout as delivered by a data source, before PacingLab derives anything from it.
 *
 * @property sourceRecordId the source's identifier, stable across edits.
 * @property origin the app that recorded it (for Fitbit: `com.fitbit.FitbitMobile`).
 * @property activity the source's activity type as a lower-case name, such as "running" or "boot_camp".
 * @property route the GPS track; empty when the workout has none, null when the source could not
 *   provide it at the moment (Health Connect only releases routes to apps in the foreground).
 */
data class SourceWorkout(
    val sourceRecordId: String,
    val origin: String,
    val device: String?,
    val activity: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val zoneOffset: ZoneOffset?,
    val lastModified: Instant,
    val laps: List<Lap>,
    val segments: List<Segment>,
    val route: Route? = Route.EMPTY,
)

/**
 * Everything recorded during a workout, already clipped to the workout's time span and restricted to
 * the app that recorded it.
 *
 * @property reportedDistanceMeters the recording app's own distance total, if it wrote one.
 */
data class WorkoutStreams(val heartRate: HeartRateSeries, val speed: SampleSeries, val route: Route, val reportedDistanceMeters: Double?) {
    companion object {
        val EMPTY = WorkoutStreams(HeartRateSeries.EMPTY, SampleSeries.EMPTY, Route.EMPTY, null)
    }
}

/** Headline numbers stored with each workout so lists don't need to decode streams. */
data class WorkoutSummary(
    val durationMillis: Long,
    val distanceMeters: Double?,
    val averageHr: Int?,
    val maxHr: Int?,
    val hasRoute: Boolean,
    val hasSpeed: Boolean,
)

/**
 * A workout as PacingLab stores it. [id] is unique across sources, for example "hc:<record id>".
 *
 * @property routePending the route could not be read yet and should be fetched again.
 */
data class Workout(
    val id: String,
    val source: DataSource,
    val sourceRecordId: String,
    val origin: String,
    val device: String?,
    val activity: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val zoneOffset: ZoneOffset?,
    val lastModified: Instant,
    val sport: Sport,
    val summary: WorkoutSummary,
    val laps: List<Lap>,
    val segments: List<Segment>,
    val routePending: Boolean = false,
)
