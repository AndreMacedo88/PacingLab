package app.pacinglab.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
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
