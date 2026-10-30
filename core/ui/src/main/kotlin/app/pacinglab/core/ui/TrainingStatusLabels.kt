package app.pacinglab.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors

/** The status in a word. */
@StringRes
fun TrainingStatusKind.label(): Int = when (this) {
    TrainingStatusKind.PRODUCTIVE -> R.string.training_status_productive
    TrainingStatusKind.MAINTAINING -> R.string.training_status_maintaining
    TrainingStatusKind.PEAKING -> R.string.training_status_peaking
    TrainingStatusKind.RECOVERY -> R.string.training_status_recovery
    TrainingStatusKind.UNPRODUCTIVE -> R.string.training_status_unproductive
    TrainingStatusKind.DETRAINING -> R.string.training_status_detraining
    TrainingStatusKind.OVERREACHING -> R.string.training_status_overreaching
    TrainingStatusKind.STRAINED -> R.string.training_status_strained
}

/** What the status means, in a sentence. */
@StringRes
fun TrainingStatusKind.detail(): Int = when (this) {
    TrainingStatusKind.PRODUCTIVE -> R.string.training_status_productive_detail
    TrainingStatusKind.MAINTAINING -> R.string.training_status_maintaining_detail
    TrainingStatusKind.PEAKING -> R.string.training_status_peaking_detail
    TrainingStatusKind.RECOVERY -> R.string.training_status_recovery_detail
    TrainingStatusKind.UNPRODUCTIVE -> R.string.training_status_unproductive_detail
    TrainingStatusKind.DETRAINING -> R.string.training_status_detraining_detail
    TrainingStatusKind.OVERREACHING -> R.string.training_status_overreaching_detail
    TrainingStatusKind.STRAINED -> R.string.training_status_strained_detail
}

/** An arrow up for progress, a check for steady, an arrow down for losing fitness, a warning for strain. */
@DrawableRes
fun TrainingStatusKind.icon(): Int = when (this) {
    TrainingStatusKind.PRODUCTIVE, TrainingStatusKind.PEAKING -> PacingLabIcons.ArrowUp
    TrainingStatusKind.MAINTAINING, TrainingStatusKind.RECOVERY -> PacingLabIcons.CheckCircle
    TrainingStatusKind.UNPRODUCTIVE, TrainingStatusKind.DETRAINING -> PacingLabIcons.ArrowDown
    TrainingStatusKind.OVERREACHING, TrainingStatusKind.STRAINED -> PacingLabIcons.Warning
}

/** The icon's colour: good, a caution, or serious. */
fun TrainingStatusKind.tint(): Color = when (this) {
    TrainingStatusKind.PRODUCTIVE, TrainingStatusKind.PEAKING, TrainingStatusKind.MAINTAINING, TrainingStatusKind.RECOVERY ->
        StatusColors.Good

    TrainingStatusKind.UNPRODUCTIVE, TrainingStatusKind.DETRAINING -> StatusColors.Warning

    TrainingStatusKind.OVERREACHING, TrainingStatusKind.STRAINED -> StatusColors.Serious
}
