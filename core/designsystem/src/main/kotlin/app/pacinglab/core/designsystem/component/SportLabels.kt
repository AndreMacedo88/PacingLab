package app.pacinglab.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport

/** Display name of the sport. */
@StringRes
fun Sport.label(): Int = when (this) {
    Sport.RUN -> R.string.sport_run
    Sport.WALK -> R.string.sport_walk
    Sport.RIDE -> R.string.sport_ride
    Sport.STRENGTH -> R.string.sport_strength
    Sport.OTHER -> R.string.sport_other
}

@DrawableRes
fun Sport.icon(): Int = when (this) {
    Sport.RUN -> PacingLabIcons.Run
    Sport.WALK -> PacingLabIcons.Walk
    Sport.RIDE -> PacingLabIcons.Ride
    Sport.STRENGTH -> PacingLabIcons.Strength
    Sport.OTHER -> PacingLabIcons.OtherWorkout
}

/** Display name of a running session's kind, such as "VO2max intervals". */
@StringRes
fun SessionKind.label(): Int = when (this) {
    SessionKind.SPRINTS -> R.string.kind_sprints
    SessionKind.VO2MAX_INTERVALS -> R.string.kind_vo2max_intervals
    SessionKind.THRESHOLD_INTERVALS -> R.string.kind_threshold_intervals
    SessionKind.TEMPO_INTERVALS -> R.string.kind_tempo_intervals
    SessionKind.INTERVALS -> R.string.kind_intervals
    SessionKind.THRESHOLD_RUN -> R.string.kind_threshold_run
    SessionKind.TEMPO_RUN -> R.string.kind_tempo_run
    SessionKind.EASY_RUN -> R.string.kind_easy_run
    SessionKind.LONG_RUN -> R.string.kind_long_run
    SessionKind.RUN -> R.string.sport_run
}
