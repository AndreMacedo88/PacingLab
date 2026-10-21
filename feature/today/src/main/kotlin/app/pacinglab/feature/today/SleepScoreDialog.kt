package app.pacinglab.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.component.InfoDialog
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.model.SleepStageType
import kotlin.math.roundToInt

/** Last night's sleep score part by part, with the measurements behind each part and how it is scored. */
@Composable
internal fun SleepScoreDialog(night: Night, score: SleepScore, needMillis: Long, onDismiss: () -> Unit) {
    fun points(fraction: Double, of: Double) = (fraction * of).roundToInt()
    val parts = buildList {
        add(
            stringResource(
                R.string.sleep_part_duration,
                points(score.duration, SleepScore.DURATION_POINTS),
                Formats.hoursMinutes(night.asleepMillis),
                Formats.hoursMinutes(needMillis),
            ),
        )
        val continuity = night.continuity
        if (score.continuity != null && continuity != null) {
            add(
                stringResource(
                    R.string.sleep_part_continuity,
                    points(score.continuity!!, SleepScore.CONTINUITY_POINTS),
                    (100.0 * night.asleepMillis / night.inBedMillis).roundToInt(),
                    (continuity.wakeAfterOnsetMillis / MINUTE).toInt(),
                    (continuity.latencyMillis / MINUTE).toInt(),
                    continuity.longAwakenings,
                ),
            )
        }
        score.depth?.let {
            add(
                stringResource(
                    R.string.sleep_part_depth,
                    points(it, SleepScore.DEPTH_POINTS),
                    share(night, SleepStageType.DEEP),
                    share(night, SleepStageType.REM),
                ),
            )
        }
        val shift = score.midpointShiftMinutes
        if (score.regularity != null && shift != null) {
            add(stringResource(R.string.sleep_part_regularity, points(score.regularity!!, SleepScore.REGULARITY_POINTS), shift))
        }
    }
    val text = stringResource(R.string.sleep_score_summary, score.score, stringResource(score.rating.label())) +
        "\n\n# " + stringResource(R.string.sleep_score_last_night) + "\n" +
        parts.joinToString("\n") { "- $it" } +
        "\n\n" + stringResource(R.string.sleep_score_info)
    InfoDialog(title = stringResource(R.string.tile_sleep), text = text, onDismiss = onDismiss)
}

private fun share(night: Night, type: SleepStageType): Int = (100.0 * (night.stageMillis[type] ?: 0) / night.asleepMillis).roundToInt()

private const val MINUTE = 60_000.0
