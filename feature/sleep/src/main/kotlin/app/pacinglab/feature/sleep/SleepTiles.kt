package app.pacinglab.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepDebtLevel
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale

/**
 * Four tiles with the last week: the night's score and the two weeks' sleep health on top, time asleep
 * and sleep debt below, each with a status.
 */
@Composable
internal fun TopTiles(loaded: SleepUiState.Loaded, night: Night, locale: Locale, modifier: Modifier = Modifier) {
    val days = (WEEK - 1 downTo 0).map { night.date.minusDays(it) }
    val labels = weekdayInitials(days, locale)
    val byDate = loaded.nights.associateBy { it.date }
    val score = loaded.scores.getValue(night.date)
    val need = loaded.needs.getValue(night.date)
    val health = loaded.health[night.date]
    val debt = loaded.debts[night.date]
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                label = stringResource(R.string.sleep_score_title),
                value = score.score.toString(),
                status = stringResource(score.rating.label()),
                good = score.rating == SleepRating.GOOD,
                modifier = Modifier.weight(1f),
            ) {
                Sparkline(
                    values = days.map { loaded.scores[it]?.score?.toDouble() },
                    band = SleepScore.GOOD_FROM.toDouble()..100.0,
                    labels = labels,
                )
            }
            Tile(
                label = stringResource(R.string.sleep_health_title),
                value = health?.score?.toString() ?: DASH,
                status = health?.let { stringResource(it.rating.label()) } ?: stringResource(R.string.sleep_health_learning),
                good = health?.rating == SleepRating.GOOD,
                modifier = Modifier.weight(1f),
            ) {
                Sparkline(
                    values = days.map { loaded.health[it]?.score?.toDouble() },
                    band = SleepScore.GOOD_FROM.toDouble()..100.0,
                    labels = labels,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val met = night.asleepMillis >= need
            Tile(
                label = stringResource(R.string.sleep_time_asleep),
                value = Formats.hoursMinutes(night.asleepMillis),
                status = stringResource(if (met) R.string.sleep_need_met else R.string.sleep_need_missed),
                good = met,
                modifier = Modifier.weight(1f),
            ) {
                Sparkline(
                    values = days.map { byDate[it]?.asleepMillis?.div(HOUR) },
                    bands = days.map { day -> loaded.needs[day]?.let { it / HOUR..Double.POSITIVE_INFINITY } },
                    color = chartColors().series[2],
                    labels = labels,
                )
            }
            Tile(
                label = stringResource(R.string.sleep_debt_title),
                value = debt?.let { Formats.hoursMinutes(it.millis) } ?: DASH,
                status = debt?.let { stringResource(it.level.label()) } ?: stringResource(R.string.sleep_health_learning),
                good = debt != null && debt.level <= SleepDebtLevel.LOW,
                modifier = Modifier.weight(1f),
            ) {
                Sparkline(
                    values = days.map { loaded.debts[it]?.millis?.div(HOUR) },
                    band = 0.0..LOW_DEBT_HOURS,
                    color = chartColors().series[2],
                    labels = labels,
                )
            }
        }
    }
}

/** Debt up to this many hours is low; the tile's band. */
private const val LOW_DEBT_HOURS = 2.0

internal fun SleepDebtLevel.label(): Int = when (this) {
    SleepDebtLevel.NONE -> R.string.sleep_debt_none
    SleepDebtLevel.LOW -> R.string.sleep_debt_low
    SleepDebtLevel.MODERATE -> R.string.sleep_debt_moderate
    SleepDebtLevel.HIGH -> R.string.sleep_debt_high
}

@Composable
private fun Tile(
    label: String,
    value: String,
    status: String,
    good: Boolean,
    modifier: Modifier = Modifier,
    chart: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall.tabularNumbers())
            chart()
            StatusChip(status, good)
        }
    }
}

/** A short status with an icon, so that the state never depends on colour alone. */
@Composable
internal fun StatusChip(text: String, good: Boolean, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painter = painterResource(if (good) PacingLabIcons.CheckCircle else PacingLabIcons.Info),
            contentDescription = null,
            tint = if (good) StatusColors.Good else StatusColors.Warning,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
