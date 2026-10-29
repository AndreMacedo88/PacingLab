package app.pacinglab.feature.workouts.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.designsystem.component.label
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.SessionKind
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
 * @property kind what a running session was for; null for other sports and while it is worked out.
 * @property load stress score of the parts with one; null when none has one or while the list does not need it.
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
    val kind: SessionKind? = null,
    val load: Double? = null,
)

fun TrainingSession.toItem(kind: SessionKind? = null): SessionItem {
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
        kind = kind,
    )
}

/** What the session was, such as "VO2max intervals"; the watch's own titles until the kind is known. */
@Composable
internal fun SessionItem.title(): String = kind?.let { stringResource(it.label()) }
    ?: titles.joinToString(" + ").ifEmpty { stringResource(sport.label()) }
