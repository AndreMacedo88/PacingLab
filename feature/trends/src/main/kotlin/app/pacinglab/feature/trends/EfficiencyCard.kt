package app.pacinglab.feature.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.data.repository.EfficiencyHistory
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Aerobic efficiency: the pace each run held at an easy heart rate, as faint dots, and the six-week
 * average as the line. Speed runs up the chart, so higher means fitter, while the axis is labelled in
 * pace.
 */
@Composable
internal fun EfficiencyCard(history: EfficiencyHistory, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    val latest = history.trend.lastOrNull() ?: return
    val reference = history.referenceHr ?: return
    val lastRun = history.points.last().date
    val dayMonth = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val change = history.trend.firstOrNull { it.date == latest.date.minusDays(CHANGE_DAYS) }?.let {
        pace(latest.speedAtReference) -
            pace(it.speedAtReference)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = stringResource(R.string.efficiency_title), info = stringResource(R.string.efficiency_info, reference))
            Column {
                Text(stringResource(R.string.efficiency_headline, reference), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = stringResource(R.string.efficiency_pace, paceText(latest.speedAtReference)),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                )
                Text(
                    text = listOfNotNull(
                        change?.let { stringResource(R.string.efficiency_change, signedSeconds(it)) },
                        stringResource(R.string.efficiency_last_run, dayMonth.format(lastRun)),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EfficiencyChart(history, today, stringResource(R.string.efficiency_description, reference), locale)
        }
    }
}

@Composable
private fun EfficiencyChart(
    history: EfficiencyHistory,
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
        val first = history.points.first().date
        val speeds = history.points.map { it.speedAtReference } + history.trend.map { it.speedAtReference }
        // Pace gridlines at whole minutes, spanning the values.
        val slowest = ceil(pace(speeds.min()) / 60).toInt()
        val fastest = floor(pace(speeds.max()) / 60).toInt()
        val low = 1000.0 / (slowest * 60)
        val high = 1000.0 / (fastest * 60)
        val axis = 18.dp.toPx()
        val plotWidth = size.width - 40.dp.toPx()
        val plotHeight = size.height - axis
        val span = (today.toEpochDay() - first.toEpochDay()).coerceAtLeast(1)
        fun x(date: LocalDate) = (date.toEpochDay() - first.toEpochDay()).toFloat() / span * plotWidth
        fun y(speed: Double) = ((high - speed) / (high - low) * plotHeight).toFloat()
        for (minutes in fastest..slowest) {
            val speed = 1000.0 / (minutes * 60)
            drawLine(colors.grid, Offset(0f, y(speed)), Offset(plotWidth, y(speed)), 1.dp.toPx())
            val layout = measurer.measure("$minutes:00", labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    plotWidth + 6.dp.toPx(),
                    (y(speed) - layout.size.height / 2).coerceIn(
                        0f,
                        plotHeight - layout.size.height,
                    ),
                ),
            )
        }
        for (point in history.points) {
            drawCircle(
                color.copy(alpha = DOT_ALPHA),
                3.dp.toPx(),
                Offset(x(point.date), y(point.speedAtReference)),
                style = Stroke(1.5.dp.toPx()),
            )
        }
        val line = Path()
        history.trend.forEachIndexed { i, day ->
            val point = Offset(x(day.date), y(day.speedAtReference))
            val joined = i > 0 && day.date.toEpochDay() - history.trend[i - 1].date.toEpochDay() == 1L
            if (joined) line.lineTo(point.x, point.y) else line.moveTo(point.x, point.y)
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

/** Seconds per kilometre at [speed] m/s. */
private fun pace(speed: Double): Double = 1000.0 / speed

private fun paceText(speed: Double): String {
    val seconds = pace(speed).roundToInt()
    return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
}

/** A pace change as "−0:08" (faster) or "+0:05" (slower). */
private fun signedSeconds(seconds: Double): String {
    val rounded = seconds.roundToInt()
    val sign = if (rounded < 0) "−" else "+"
    val absolute = abs(rounded)
    return "%s%d:%02d".format(Locale.ROOT, sign, absolute / 60, absolute % 60)
}

/** The change shown beside the headline covers six weeks. */
private const val CHANGE_DAYS = 42L
private const val DOT_ALPHA = 0.55f
