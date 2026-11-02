package app.pacinglab.feature.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.strain.StrainLevel
import app.pacinglab.core.data.strain.StrainHistory
import app.pacinglab.core.designsystem.chart.LevelSparkline
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.ui.ExperimentalPill
import app.pacinglab.core.ui.StrainPill
import app.pacinglab.core.ui.label
import app.pacinglab.core.ui.strainLevels
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import app.pacinglab.core.ui.R as UiR

/**
 * Daytime strain: the latest day's score with its level and an "experimental" mark, the excess in bpm, the
 * last four weeks on the four levels, and on a raised day what else in the data could explain it. Until
 * there are enough days it counts towards them. The method and how far to trust it are in the info.
 */
@Composable
internal fun StrainCard(history: StrainHistory?, locale: Locale, modifier: Modifier = Modifier) {
    TrendCard(
        title = stringResource(UiR.string.strain_title),
        info = stringResource(R.string.strain_info),
        modifier = modifier,
        advanced = stringResource(R.string.strain_advanced),
    ) {
        val latest = history?.latest
        if (history == null || latest == null) {
            Learning(history?.progress ?: 0)
            return@TrendCard
        }
        val day = when (latest.date) {
            history.today -> stringResource(R.string.strain_so_far)
            history.today.minusDays(1) -> stringResource(R.string.strain_yesterday)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(latest.date)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(latest.score.toString(), style = MaterialTheme.typography.headlineMedium.tabularNumbers())
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(day, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = stringResource(UiR.string.strain_excess, signedOne(latest.excessBpm, locale)),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            latest.level?.let { StrainPill(it) }
            ExperimentalPill()
        }
        val byDate = history.days.associate { it.date to it.score?.toDouble() }
        val description = stringResource(R.string.strain_chart)
        LevelSparkline(
            values = (CHART_DAYS - 1 downTo 0).map { byDate[history.today.minusDays(it.toLong())] },
            levels = strainLevels(),
            scale = SCALE,
            modifier = Modifier.semantics { contentDescription = description },
            // Tall enough for the narrow raised band to carry its name.
            height = 88.dp,
        )
        if (latest.level == StrainLevel.RAISED || latest.level == StrainLevel.HIGH) {
            val causes = history.causes[latest.date].orEmpty()
            Text(
                text = if (causes.isEmpty()) {
                    stringResource(UiR.string.strain_no_cause)
                } else {
                    stringResource(UiR.string.strain_causes, causes.map { stringResource(it.label()) }.joinToString(", "))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Learning(days: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shown = days.coerceAtMost(StrainHistory.NEEDED_DAYS)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(UiR.plurals.strain_learning, shown, shown, StrainHistory.NEEDED_DAYS),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            ExperimentalPill()
        }
        LinearProgressIndicator(
            progress = { shown.toFloat() / StrainHistory.NEEDED_DAYS },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(R.string.strain_learning_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun signedOne(value: Double, locale: Locale): String {
    val text = String.format(locale, "%.1f", abs(value))
    return if (value < 0) "−$text" else "+$text"
}

private const val CHART_DAYS = 28
private val SCALE = 25.0..85.0
