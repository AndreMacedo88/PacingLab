package app.pacinglab.core.ui

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.analytics.strain.DaytimeStrain
import app.pacinglab.core.analytics.strain.StrainCause
import app.pacinglab.core.analytics.strain.StrainLevel
import app.pacinglab.core.designsystem.chart.ChartLevel
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors

@StringRes
fun StrainLevel.label(): Int = when (this) {
    StrainLevel.CALM -> R.string.strain_level_calm
    StrainLevel.USUAL -> R.string.strain_level_usual
    StrainLevel.RAISED -> R.string.strain_level_raised
    StrainLevel.HIGH -> R.string.strain_level_high
}

@StringRes
fun StrainCause.label(): Int = when (this) {
    StrainCause.VITALS_OFF -> R.string.strain_cause_vitals
    StrainCause.HARD_TRAINING -> R.string.strain_cause_training
    StrainCause.POOR_SLEEP -> R.string.strain_cause_sleep
}

/** The day's level in a word with an icon: calm and usual in the calm colours, raised and high as warnings. */
@Composable
fun StrainPill(level: StrainLevel, modifier: Modifier = Modifier) {
    val text = stringResource(level.label())
    when (level) {
        StrainLevel.CALM -> StatusPill(text, chartColors().positive, PacingLabIcons.ArrowDown, modifier)
        StrainLevel.USUAL -> StatusPill(text, StatusColors.Good, PacingLabIcons.CheckCircle, modifier)
        StrainLevel.RAISED -> StatusPill(text, StatusColors.Warning, PacingLabIcons.ArrowUp, modifier)
        StrainLevel.HIGH -> StatusPill(text, StatusColors.Serious, PacingLabIcons.ArrowUp, modifier)
    }
}

/** Marks a measure that rests on thin evidence, so that it is read as a hint. */
@Composable
fun ExperimentalPill(modifier: Modifier = Modifier) {
    StatusPill(stringResource(R.string.strain_experimental), MaterialTheme.colorScheme.outline, PacingLabIcons.Info, modifier)
}

/** The four levels on the score's scale, for a [app.pacinglab.core.designsystem.chart.LevelSparkline]. */
@Composable
fun strainLevels(): List<ChartLevel> = listOf(
    ChartLevel(0.0..DaytimeStrain.USUAL_FROM.toDouble(), chartColors().positive, stringResource(R.string.strain_level_calm)),
    ChartLevel(
        DaytimeStrain.USUAL_FROM.toDouble()..DaytimeStrain.RAISED_FROM.toDouble(),
        StatusColors.Good,
        stringResource(R.string.strain_level_usual),
    ),
    ChartLevel(
        DaytimeStrain.RAISED_FROM.toDouble()..DaytimeStrain.HIGH_FROM.toDouble(),
        StatusColors.Warning,
        stringResource(R.string.strain_level_raised),
    ),
    ChartLevel(DaytimeStrain.HIGH_FROM.toDouble()..100.0, StatusColors.Serious, stringResource(R.string.strain_level_high)),
)
