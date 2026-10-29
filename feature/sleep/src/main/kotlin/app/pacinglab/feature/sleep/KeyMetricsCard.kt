package app.pacinglab.feature.sleep

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.SleepStageType
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Key measures over the last week or month, ending with the selected night. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyMetricsCard(
    nights: List<Night>,
    until: LocalDate,
    scores: Map<LocalDate, SleepScore>,
    needs: Map<LocalDate, Long>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    var span by rememberSaveable { mutableIntStateOf(0) }
    val count = if (span == 0) WEEK else MONTH
    val days = (count - 1 downTo 0).map { until.minusDays(it) }
    val byDate = nights.associateBy { it.date }
    val colors = chartColors()
    fun series(value: (Night) -> Double?) = days.map { byDate[it]?.let(value) }
    SleepCard(stringResource(R.string.sleep_metrics_title), stringResource(R.string.sleep_metrics_info), modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(R.string.sleep_metrics_week, R.string.sleep_metrics_month).forEachIndexed { i, label ->
                SegmentedButton(selected = span == i, onClick = {
                    span = i
                }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(label)) }
            }
        }
        // Weekday initials fit under a week; a month is read as a shape.
        val labels = if (count == WEEK) weekdayInitials(days, locale) else null
        val scoreValues = days.map { scores[it]?.score?.toDouble() }
        MetricRow(stringResource(R.string.sleep_score_title), average(scoreValues)?.roundToInt()?.toString()) {
            Sparkline(values = scoreValues, band = SleepScore.GOOD_FROM.toDouble()..100.0, labels = labels)
        }
        val asleep = series { it.asleepMillis / HOUR }
        MetricRow(stringResource(R.string.sleep_time_asleep), average(asleep)?.let { Formats.hoursMinutes((it * HOUR).toLong()) }) {
            Sparkline(
                values = asleep,
                bands = days.map { day -> needs[day]?.let { it / HOUR..Double.POSITIVE_INFINITY } },
                color = colors.series[2],
                labels = labels,
            )
        }
        Text(stringResource(R.string.sleep_metrics_windows), style = MaterialTheme.typography.labelLarge)
        SleepWindows(days.mapNotNull { byDate[it] }, until, stringResource(R.string.sleep_schedule_description), locale)
        val efficiency = series { 100.0 * it.asleepMillis / it.inBedMillis }
        MetricRow(
            stringResource(R.string.sleep_quality_efficiency),
            average(efficiency)?.let {
                stringResource(R.string.sleep_percent, it.roundToInt())
            },
        ) {
            Sparkline(values = efficiency, band = 85.0..100.0, labels = labels)
        }
        for (type in listOf(SleepStageType.DEEP, SleepStageType.REM)) {
            val typical = type.typical() ?: continue
            val shares = series { n -> (n.stageMillis[type] ?: 0L).takeIf { it > 0 }?.let { 100.0 * it / n.asleepMillis } }
            MetricRow(
                stringResource(R.string.sleep_quality_stage, stringResource(type.label())),
                average(shares)?.let {
                    stringResource(R.string.sleep_percent, it.roundToInt())
                },
            ) {
                // Published ranges, not the athlete's own: a habit of short deep sleep should not look normal.
                Sparkline(
                    values = shares,
                    band = typical.first.toDouble()..typical.last.toDouble(),
                    color = colors.stage(type),
                    labels = labels,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(name: String, average: String?, modifier: Modifier = Modifier, chart: @Composable () -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text(
                text = average?.let { stringResource(R.string.sleep_metrics_average, it) } ?: DASH,
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1.2f)) { chart() }
    }
}

private fun average(values: List<Double?>): Double? = values.filterNotNull().takeIf { it.isNotEmpty() }?.average()
