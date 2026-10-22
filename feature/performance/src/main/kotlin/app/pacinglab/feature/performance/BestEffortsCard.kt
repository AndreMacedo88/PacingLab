package app.pacinglab.feature.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.BestPair
import app.pacinglab.core.analytics.performance.Effort
import app.pacinglab.core.analytics.performance.EffortBests
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/** Which runs the best efforts come from. */
enum class EffortRange(val label: Int) {
    RECENT(R.string.efforts_recent),
    ALL(R.string.efforts_all),
}

/**
 * Best efforts: the speed-duration curve (pace against duration on a log scale) and the fastest time
 * over standard distances. GPS efforts are drawn solid in the series colour; efforts from the watch's
 * own speed, which nothing can check, are dashed and grey, with hollow dots, and set in italics in the
 * table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BestEffortsCard(
    recent: EffortBests,
    allTime: EffortBests,
    initialRange: EffortRange,
    onOpenWorkout: (String) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(initialRange) }
    val bests = if (range == EffortRange.RECENT) recent else allTime
    PerformanceCard(stringResource(R.string.efforts_title), stringResource(R.string.efforts_info), modifier) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            EffortRange.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = range == option,
                    onClick = { range = option },
                    shape = SegmentedButtonDefaults.itemShape(i, EffortRange.entries.size),
                ) { Text(stringResource(option.label)) }
            }
        }
        if (bests.isEmpty()) {
            Text(
                stringResource(R.string.efforts_none_recent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SpeedDurationChart(bests.durations, locale)
            Legend()
            DistanceTable(bests.distances, onOpenWorkout, locale)
        }
    }
}

@Composable
private fun SpeedDurationChart(durations: Map<Int, BestPair>, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val gpsColor = colors.series[0]
    val watchColor = colors.muted
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val gps = durations.mapNotNull { (t, pair) -> pair.gps?.let { t to it.seconds / it.meters * 1000 } }
    val watch = durations.mapNotNull { (t, pair) -> pair.watch?.let { t to it.seconds / it.meters * 1000 } }
    val paces = (gps + watch).map { it.second }
    val fastest = floor(paces.min() / PACE_STEP) * PACE_STEP
    val slowest = ceil(paces.max() / PACE_STEP) * PACE_STEP
    val step = if (slowest - fastest > 4 * 60) 2 * PACE_STEP else PACE_STEP
    val longest = durations.keys.max().toDouble()
    val description = stringResource(R.string.efforts_chart_description, durations.size)
    val ticks = DURATION_TICKS.map { seconds ->
        seconds to
            if (seconds <
                3_600
            ) {
                stringResource(R.string.efforts_tick_minutes, seconds / 60)
            } else {
                stringResource(
                    R.string.efforts_tick_hours,
                    seconds / 3_600,
                )
            }
    }
    Canvas(modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
        val left = 40.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 6.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val logMin = ln(SHORTEST.toDouble())
        val logSpan = (ln(longest) - logMin).coerceAtLeast(0.1)
        fun x(seconds: Int) = (left + (ln(seconds.toDouble()) - logMin) / logSpan * (right - left)).toFloat()
        fun y(pace: Double) = (top + (pace - fastest) / (slowest - fastest).coerceAtLeast(1.0) * (bottom - top)).toFloat()

        var tick = fastest
        while (tick <= slowest + 0.1) {
            drawLine(colors.grid, Offset(left, y(tick)), Offset(right, y(tick)), 1.dp.toPx())
            val label = measurer.measure(paceLabel(tick), style)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(tick) - label.size.height / 2f))
            tick += step
        }
        for ((seconds, text) in ticks) {
            if (seconds > longest) continue
            val label = measurer.measure(text, style)
            val at = x(seconds).coerceIn(left + label.size.width / 2f, right - label.size.width / 2f)
            drawText(label, topLeft = Offset(at - label.size.width / 2f, bottom + 4.dp.toPx()))
        }
        series(watch, ::x, ::y, watchColor, ring, dashed = true)
        series(gps, ::x, ::y, gpsColor, ring, dashed = false)
    }
}

private fun DrawScope.series(
    points: List<Pair<Int, Double>>,
    x: (Int) -> Float,
    y: (Double) -> Float,
    color: Color,
    ring: Color,
    dashed: Boolean,
) {
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { i, (t, pace) -> if (i == 0) path.moveTo(x(t), y(pace)) else path.lineTo(x(t), y(pace)) }
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    for ((t, pace) in points) {
        val center = Offset(x(t), y(pace))
        drawCircle(ring, 5.dp.toPx(), center)
        if (dashed) {
            drawCircle(color, 3.5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        } else {
            drawCircle(color, 3.5.dp.toPx(), center)
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val colors = chartColors()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(stringResource(R.string.efforts_gps), colors.series[0], dashed = false)
        LegendItem(stringResource(R.string.efforts_watch_legend), colors.muted, dashed = true)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.width(24.dp).height(10.dp)) {
            val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = effect)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DistanceTable(
    distances: Map<RaceDistance, BestPair>,
    onOpenWorkout: (String) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Header(stringResource(R.string.efforts_distance), Modifier.weight(1f), TextAlign.Start)
            Header(stringResource(R.string.efforts_gps), Modifier.weight(1f))
            Header(stringResource(R.string.efforts_watch), Modifier.weight(1f))
        }
        for ((distance, pair) in distances) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(distance.label(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TimeCell(pair.gps, estimated = false, date, onOpenWorkout, Modifier.weight(1f))
                TimeCell(pair.watch, estimated = true, date, onOpenWorkout, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TimeCell(
    effort: Effort?,
    estimated: Boolean,
    date: DateTimeFormatter,
    onOpenWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (estimated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = (if (effort != null) modifier.clickable { onOpenWorkout(effort.workoutId) } else modifier).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = effort?.let { raceTime(it.seconds) } ?: "–",
            style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
            fontStyle = if (estimated) FontStyle.Italic else FontStyle.Normal,
            color = color,
        )
        effort?.let {
            Text(date.format(it.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.End) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier,
    )
}

private fun paceLabel(secondsPerKm: Double): String {
    val total = secondsPerKm.toInt()
    return "%d:%02d".format(Locale.ROOT, total / 60, total % 60)
}

private const val SHORTEST = 60
private const val PACE_STEP = 30.0
private val DURATION_TICKS = listOf(60, 300, 600, 1_800, 3_600, 7_200)
