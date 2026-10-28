package app.pacinglab.feature.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.Standing
import app.pacinglab.core.designsystem.chart.ChartLevel
import app.pacinglab.core.designsystem.chart.LevelSparkline
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import kotlin.math.roundToInt

/**
 * Readiness: the score and its level once the HRV baseline has a week of nights (marked provisional until
 * it has two), progress towards it before, the last week's scores on the levels' bands, and one row per
 * contributor with where it stands against the athlete's own normal.
 *
 * @param history readiness on each of the last days, oldest first, ending with [readiness].
 * @param days weekday initials of [history].
 */
@Composable
internal fun ReadinessCard(
    readiness: Readiness,
    history: List<Readiness>,
    days: List<String>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = stringResource(R.string.readiness_title), info = stringResource(R.string.readiness_info))
            val score = readiness.score
            val level = readiness.level
            if (score != null && level != null) {
                Score(score, level)
                if (readiness.provisional) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.readiness_provisional,
                            Readiness.BASELINE_NIGHTS,
                            readiness.baselineNights,
                            Readiness.BASELINE_NIGHTS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (history.count { it.score != null } > 1) Week(history, days)
            } else {
                Calibrating(readiness.baselineNights)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Contributors(readiness)
            if (readiness.breathingElevated) {
                Flag(stringResource(R.string.readiness_breathing_raised), StatusColors.Serious)
            }
        }
    }
}

@Composable
private fun Score(score: Int, level: ReadinessLevel, modifier: Modifier = Modifier) {
    val (label, color) = when (level) {
        ReadinessLevel.READY -> R.string.readiness_ready to StatusColors.Good
        ReadinessLevel.MODERATE -> R.string.readiness_moderate to StatusColors.Warning
        ReadinessLevel.LOW -> R.string.readiness_low to StatusColors.Serious
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(score.toString(), style = MaterialTheme.typography.displaySmall.tabularNumbers())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(levelIcon(level)), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            }
        }
        LinearProgressIndicator(
            progress = { score / 100f },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The last week's scores on the bands of the three levels, coloured by the level each falls in. */
@Composable
private fun Week(history: List<Readiness>, days: List<String>, modifier: Modifier = Modifier) {
    LevelSparkline(
        values = history.map { it.score?.toDouble() },
        levels = listOf(
            ChartLevel(
                WEEK_SCALE_FLOOR..Readiness.MODERATE_FROM.toDouble(),
                StatusColors.Serious,
                stringResource(R.string.readiness_level_low),
            ),
            ChartLevel(
                Readiness.MODERATE_FROM.toDouble()..Readiness.READY_FROM.toDouble(),
                StatusColors.Warning,
                stringResource(R.string.readiness_level_moderate),
            ),
            ChartLevel(Readiness.READY_FROM.toDouble()..100.0, StatusColors.Good, stringResource(R.string.readiness_level_ready)),
        ),
        scale = WEEK_SCALE_FLOOR..100.0,
        labels = days,
        modifier = modifier,
    )
}

/** Scores below this sit on the bottom edge of the week chart, so that the three levels get room. */
private const val WEEK_SCALE_FLOOR = 30.0

private fun levelIcon(level: ReadinessLevel): Int = when (level) {
    ReadinessLevel.READY -> PacingLabIcons.CheckCircle
    ReadinessLevel.MODERATE -> PacingLabIcons.Info
    ReadinessLevel.LOW -> PacingLabIcons.Warning
}

@Composable
private fun Calibrating(nights: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.readiness_calibrating), style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { (nights.toFloat() / Readiness.BASELINE_NIGHTS).coerceAtMost(1f) },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = pluralStringResource(
                R.plurals.readiness_calibrating_detail,
                Readiness.BASELINE_NIGHTS,
                nights,
                Readiness.BASELINE_NIGHTS,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Contributors(readiness: Readiness, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // While calibrating, the progress above already tells how far the HRV baseline is.
        readiness.hrv?.let { hrv ->
            ContributorRow(
                name = stringResource(R.string.readiness_hrv),
                value = stringResource(
                    R.string.readiness_hrv_value,
                    hrv.weekAverageMs.roundToInt(),
                    hrv.normalLowMs.roundToInt(),
                    hrv.normalHighMs.roundToInt(),
                ),
                standing = hrv.standing,
                below = R.string.standing_low,
                above = R.string.standing_high,
            )
        }
        readiness.restingHr?.let {
            ContributorRow(
                name = stringResource(R.string.readiness_resting_hr),
                value = stringResource(R.string.readiness_resting_hr_value, it.lastNight.roundToInt(), it.usual.roundToInt()),
                standing = it.standing,
                below = R.string.standing_low,
                above = R.string.standing_raised,
                aboveIsBad = true,
            )
        }
        readiness.sleep?.let {
            ContributorRow(
                name = stringResource(R.string.readiness_sleep),
                value = stringResource(R.string.readiness_sleep_value, it.lastNight.score, Formats.hoursMinutes(it.lastNightMillis)),
                standing = it.standing,
                below = R.string.standing_poor,
                above = R.string.standing_normal,
            )
        }
        readiness.training?.let {
            ContributorRow(
                name = stringResource(R.string.readiness_training),
                value = stringResource(R.string.readiness_training_value, signed(it.form), it.band.roundToInt()),
                standing = it.standing,
                below = R.string.standing_strained,
                above = R.string.standing_fresh,
            )
        }
    }
}

/**
 * A contributor with its value and where it stands. Normal reads as plain text; a departure gets an icon
 * and a word as well as colour, so it never relies on colour alone.
 */
@Composable
private fun ContributorRow(
    name: String,
    value: String,
    standing: Standing?,
    @StringRes below: Int,
    @StringRes above: Int,
    modifier: Modifier = Modifier,
    aboveIsBad: Boolean = false,
) {
    val word = when (standing) {
        null -> null
        Standing.NORMAL -> stringResource(R.string.standing_normal)
        Standing.BELOW -> stringResource(below)
        Standing.ABOVE -> stringResource(above)
    }
    val warn = standing == Standing.BELOW || (aboveIsBad && standing == Standing.ABOVE)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = listOfNotNull(name, value, word).joinToString(", ")
        },
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        if (word != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (warn) {
                    Icon(
                        painterResource(PacingLabIcons.Warning),
                        contentDescription = null,
                        tint = StatusColors.Serious,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(word, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun Flag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(PacingLabIcons.Warning), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun signed(value: Double): String {
    val rounded = value.roundToInt()
    return when {
        rounded > 0 -> "+$rounded"
        rounded < 0 -> "−${-rounded}"
        else -> "0"
    }
}
