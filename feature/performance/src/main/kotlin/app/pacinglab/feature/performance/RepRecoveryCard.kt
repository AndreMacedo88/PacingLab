package app.pacinglab.feature.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.data.repository.SessionRecoveryPoint
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Heart rate recovery after reps: for each interval session, how far heart rate fell in the minute after
 * its hard reps (the median over the session), as dots, with the median of the last five sessions as
 * the line. A faster drop comes with better aerobic fitness; a slower one than usual with fatigue
 * (Daanen 2012; Buchheit 2014).
 */
@Composable
internal fun RepRecoveryCard(points: List<SessionRecoveryPoint>, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val trend = rollingMedian(points)
    val latest = trend.last()
    val earlier = trend.lastOrNull { it.first <= latest.first.minusDays(CHANGE_DAYS) }
    val dayMonth = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    PerformanceCard(stringResource(R.string.hrr_title), stringResource(R.string.hrr_info), modifier) {
        Column {
            Text(stringResource(R.string.hrr_headline), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.hrr_value, latest.second.roundToInt()),
                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            )
            Text(
                text = listOfNotNull(
                    earlier?.let { stringResource(R.string.hrr_change, "%+d".format(locale, (latest.second - it.second).roundToInt())) },
                    pluralStringResource(R.plurals.hrr_sessions, points.size, points.size),
                    stringResource(R.string.hrr_last, dayMonth.format(points.last().date)),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RecoveryChart(points, trend, today, stringResource(R.string.hrr_description), locale)
    }
}

/** The median drop of each session and the four before it, by session date. */
private fun rollingMedian(points: List<SessionRecoveryPoint>): List<Pair<LocalDate, Double>> = points.indices.map { i ->
    val window = points.subList(maxOf(0, i - ROLLING + 1), i + 1).map { it.recovery.dropBpm }.sorted()
    val middle = window.size / 2
    points[i].date to if (window.size % 2 == 1) window[middle] else (window[middle - 1] + window[middle]) / 2
}

@Composable
private fun RecoveryChart(
    points: List<SessionRecoveryPoint>,
    trend: List<Pair<LocalDate, Double>>,
    today: LocalDate,
    description: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val color = colors.series[0]
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val month = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    Canvas(modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val first = points.first().date
        val drops = points.map { it.recovery.dropBpm }
        val low = floor(drops.min() / GRID) * GRID
        val high = maxOf(ceil(drops.max() / GRID) * GRID, low + GRID)
        val axis = 18.dp.toPx()
        val plotWidth = size.width - 32.dp.toPx()
        val plotHeight = size.height - axis
        val span = (today.toEpochDay() - first.toEpochDay()).coerceAtLeast(1)
        fun x(date: LocalDate) = (date.toEpochDay() - first.toEpochDay()).toFloat() / span * plotWidth
        fun y(bpm: Double) = ((high - bpm) / (high - low) * plotHeight).toFloat()
        var bpm = low
        while (bpm <= high) {
            drawLine(colors.grid, Offset(0f, y(bpm)), Offset(plotWidth, y(bpm)), 1.dp.toPx())
            val layout = measurer.measure(bpm.roundToInt().toString(), labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    plotWidth + 6.dp.toPx(),
                    (y(bpm) - layout.size.height / 2).coerceIn(
                        0f,
                        plotHeight - layout.size.height,
                    ),
                ),
            )
            bpm += GRID
        }
        for (point in points) {
            drawCircle(
                color.copy(alpha = DOT_ALPHA),
                3.dp.toPx(),
                Offset(x(point.date), y(point.recovery.dropBpm)),
                style = Stroke(1.5.dp.toPx()),
            )
        }
        val line = Path()
        trend.forEachIndexed { i, (date, value) ->
            if (i == 0) line.moveTo(x(date), y(value)) else line.lineTo(x(date), y(value))
        }
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(colors.baseline, Offset(0f, plotHeight), Offset(plotWidth, plotHeight), 1.dp.toPx())
        var date = first.withDayOfMonth(1).plusMonths(1)
        val months = if (span > 400) 2L else 1L
        var free = 0f
        while (!date.isAfter(today)) {
            val label = if (date.monthValue == 1) date.year.toString() else month.format(date)
            val layout = measurer.measure(label, labelStyle)
            if (x(date) >= free && x(date) + layout.size.width <= plotWidth) {
                drawLine(colors.baseline, Offset(x(date), plotHeight), Offset(x(date), plotHeight + 4.dp.toPx()), 1.dp.toPx())
                drawText(layout, topLeft = Offset(x(date) + 2.dp.toPx(), size.height - layout.size.height))
                free = x(date) + layout.size.width + 8.dp.toPx()
            }
            date = date.plusMonths(months)
        }
    }
}

/** Sessions in the rolling median. */
private const val ROLLING = 5

/** The change beside the headline compares with the trend six weeks earlier. */
private const val CHANGE_DAYS = 42L
private const val GRID = 10.0
private const val DOT_ALPHA = 0.55f
