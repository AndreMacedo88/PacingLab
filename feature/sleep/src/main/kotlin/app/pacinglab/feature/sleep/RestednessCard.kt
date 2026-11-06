package app.pacinglab.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.RestednessEstimate
import app.pacinglab.core.analytics.recovery.ScoreComparison
import app.pacinglab.core.analytics.recovery.SleepFactors
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.restedWord
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.ui.ExperimentalPill
import app.pacinglab.core.ui.RestednessChart
import app.pacinglab.core.ui.RestednessPill
import app.pacinglab.core.ui.formatRestedness
import app.pacinglab.core.ui.nearestAnswer
import java.time.LocalDate
import app.pacinglab.core.ui.R as UiR

/**
 * How rested the athlete can expect to feel on the [morning] shown, learnt from their own answers: the
 * expected answer, where it lies against their usual one, the week's expected line with the answers as
 * rings, and whether it follows the answers more closely than the sleep score. Until there are enough
 * rated mornings, or while the nights do not predict them, the card says so instead of showing a number.
 */
@Composable
internal fun RestednessCard(estimate: RestednessEstimate?, morning: LocalDate, rested: Map<LocalDate, Int>, modifier: Modifier = Modifier) {
    SleepCard(
        stringResource(UiR.string.restedness_title),
        stringResource(R.string.sleep_restedness_info),
        modifier,
        advanced = stringResource(R.string.sleep_restedness_advanced),
    ) {
        when {
            estimate == null -> Text(
                text = stringResource(R.string.sleep_restedness_computing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            estimate.learning -> Learning(estimate.mornings)

            !estimate.predictive -> NoPattern(estimate)

            else -> Estimate(estimate, morning, rested)
        }
    }
}

@Composable
private fun Learning(mornings: Int, modifier: Modifier = Modifier) {
    val needed = SleepFactors.MIN_NIGHTS
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(UiR.plurals.restedness_learning, needed, mornings, needed),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            ExperimentalPill()
        }
        LinearProgressIndicator(
            progress = { mornings.toFloat() / needed },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(R.string.sleep_restedness_learning_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoPattern(estimate: RestednessEstimate, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sleep_restedness_no_pattern),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            ExperimentalPill()
        }
        Text(
            text = stringResource(
                R.string.sleep_restedness_no_pattern_detail,
                formatError(estimate.error ?: 0.0, locale),
                formatError(estimate.averageError ?: 0.0, locale),
                estimate.mornings,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Estimate(estimate: RestednessEstimate, morning: LocalDate, rested: Map<LocalDate, Int>, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val value = estimate.expected[morning]
    val answer = rested[morning]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value?.let { formatRestedness(it, locale) } ?: DASH,
                style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = value?.let { restedWord(nearestAnswer(it)) } ?: stringResource(R.string.sleep_restedness_no_night),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (answer != null) {
                        stringResource(R.string.sleep_restedness_answer, answer, restedWord(answer).lowercase(locale))
                    } else {
                        stringResource(R.string.sleep_restedness_usual, formatRestedness(estimate.usual ?: 0.0, locale))
                    },
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            estimate.standing(morning)?.let { RestednessPill(it) }
            ExperimentalPill()
        }
        val days = (WEEK - 1 downTo 0).map { morning.minusDays(it) }
        RestednessChart(
            expected = days.map { estimate.expected[it] },
            answers = days.map { rested[it] },
            usual = estimate.usual,
            labels = weekdayInitials(days, locale),
            height = 64.dp,
        )
        estimate.comparison?.let { comparison ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val closer = comparison == ScoreComparison.CLOSER
                Icon(
                    painter = painterResource(if (closer) PacingLabIcons.CheckCircle else PacingLabIcons.Info),
                    contentDescription = null,
                    tint = if (closer) StatusColors.Good else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(comparison.label()), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(
                            R.string.sleep_restedness_errors,
                            formatError(estimate.error ?: 0.0, locale),
                            formatError(estimate.scoreError ?: 0.0, locale),
                        ),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ScoreComparison.label(): Int = when (this) {
    ScoreComparison.CLOSER -> R.string.sleep_restedness_closer
    ScoreComparison.ABOUT_THE_SAME -> R.string.sleep_restedness_same
    ScoreComparison.FURTHER -> R.string.sleep_restedness_further
}

/** A typical error in steps of the rating, to two decimals. */
private fun formatError(value: Double, locale: java.util.Locale): String = String.format(locale, "%.2f", value)
