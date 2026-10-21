package app.pacinglab.feature.workouts.list

import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** How a session came to be recorded, which tells training apart from everyday movement. */
enum class Capture {
    /** Started deliberately on the watch: almost always training. */
    RECORDED,

    /** Recognised from movement by an app on its own, such as a walk to the shops. */
    DETECTED,

    /** Typed in by hand. */
    ENTERED,
}

/**
 * A training session as the list shows it.
 *
 * @property titles distinct titles of the parts, as the watch named them ("Run", "Interval workout").
 * @property movingMillis sum of the parts' durations, without the pauses between them.
 * @property capture recorded when any part was started on the watch.
 * @property origin the app that wrote the first part.
 */
data class SessionItem(
    val id: String,
    val sport: Sport,
    val titles: List<String>,
    val parts: Int,
    val start: Instant,
    val zoneOffset: ZoneOffset?,
    val movingMillis: Long,
    val distanceMeters: Double?,
    val averageHr: Int?,
    val maxHr: Int?,
    val hasRoute: Boolean,
    val capture: Capture = Capture.RECORDED,
    val origin: String = "",
)

fun TrainingSession.toItem(): SessionItem {
    val hrWeighted = workouts.filter { it.summary.averageHr != null }
    val hrMillis = hrWeighted.sumOf { it.summary.durationMillis }
    return SessionItem(
        id = id,
        sport = sport,
        titles = workouts.mapNotNull { it.title?.takeIf(String::isNotBlank) }.distinct(),
        parts = workouts.size,
        start = start,
        zoneOffset = workouts.first().zoneOffset,
        movingMillis = workouts.sumOf { it.summary.durationMillis },
        distanceMeters = workouts.mapNotNull { it.summary.distanceMeters }.takeIf { it.isNotEmpty() }?.sum(),
        averageHr = if (hrMillis == 0L) {
            null
        } else {
            (hrWeighted.sumOf { it.summary.averageHr!!.toDouble() * it.summary.durationMillis } / hrMillis).roundToInt()
        },
        maxHr = workouts.mapNotNull { it.summary.maxHr }.maxOrNull(),
        hasRoute = workouts.any { it.summary.hasRoute },
        capture = when {
            workouts.any { it.recordingMethod == RecordingMethod.ACTIVE } -> Capture.RECORDED
            workouts.all { it.recordingMethod == RecordingMethod.MANUAL } -> Capture.ENTERED
            else -> Capture.DETECTED
        },
        origin = workouts.first().origin,
    )
}
