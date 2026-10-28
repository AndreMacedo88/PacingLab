package app.pacinglab.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.theme.chartColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Daily values over a period, for a metric's detail page: each day's usual range shaded as steps in the
 * line's colour, the values as a line with dots (out-of-range orange outside their day's range, or the
 * colour of the level they fall in when [levels] are given), an optional [trend] drawn over them, and
 * the [selected] day marked. Tapping or dragging across the chart selects the nearest day with a value.
 *
 * @param values one per day, oldest first; null for days without one.
 * @param bands the usual range of each day, or null where there is none.
 * @param labels day indices with the label to write under them.
 * @param format turns a value on the scale into its axis label.
 */
@Composable
fun HistoryChart(
    values: List<Double?>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    bands: List<ClosedFloatingPointRange<Double>?> = emptyList(),
    levels: List<ChartLevel> = emptyList(),
    scale: ClosedFloatingPointRange<Double>? = null,
    trend: List<Double?> = emptyList(),
    labels: List<Pair<Int, String>> = emptyList(),
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
    format: (Double) -> String = { it.roundToInt().toString() },
    height: Dp = 200.dp,
) {
    val colors = chartColors()
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val ink = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val count = values.size
    val known = values.filterNotNull() + bands.filterNotNull().flatMap { listOf(it.start, it.endInclusive) } + trend.filterNotNull()
    if (count == 0 || known.isEmpty()) return
    val (low, high) = scale?.let { it.start to it.endInclusive } ?: niceRange(known.min(), known.max())
    fun nearest(x: Float, width: Float, axis: Float): Int? {
        val step = (width - axis) / count
        val index = ((x - step / 2) / step).roundToInt().coerceIn(0, count - 1)
        return values.indices.filter { values[it] != null }.minByOrNull { abs(it - index) }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(values) {
                detectTapGestures { offset -> nearest(offset.x, size.width.toFloat(), AXIS.toPx())?.let(onSelect) }
            }
            .pointerInput(values) {
                detectHorizontalDragGestures { change, _ ->
                    nearest(change.position.x, size.width.toFloat(), AXIS.toPx())?.let(onSelect)
                }
            },
    ) {
        val axis = AXIS.toPx()
        val bottom = size.height - LABELS.toPx()
        val width = size.width - axis
        val step = width / count
        fun x(index: Int) = step * index + step / 2
        fun y(value: Double) = ((high - value) / (high - low) * (bottom - TOP.toPx()) + TOP.toPx()).toFloat()

        for (level in levels) {
            val top = y(level.range.endInclusive.coerceAtMost(high))
            val end = y(level.range.start.coerceAtLeast(low))
            drawRect(level.color.copy(alpha = LEVEL_ALPHA), Offset(0f, top), Size(width, end - top))
        }
        bands.forEachIndexed { i, band ->
            if (band == null) return@forEachIndexed
            val top = y(band.endInclusive)
            drawRect(color.copy(alpha = BAND_ALPHA), Offset(x(i) - step / 2, top), Size(step, y(band.start) - top))
        }
        for (tick in ticks(low, high)) {
            drawLine(colors.grid, Offset(0f, y(tick)), Offset(width, y(tick)), 1.dp.toPx())
            val label = measurer.measure(format(tick), style)
            drawText(label, topLeft = Offset(width + 6.dp.toPx(), y(tick) - label.size.height / 2f))
        }
        for ((index, text) in labels) {
            val label = measurer.measure(text, style)
            val left = (x(index) - label.size.width / 2f).coerceIn(0f, width - label.size.width)
            drawText(label, topLeft = Offset(left, bottom + 4.dp.toPx()))
        }

        fun pointColor(i: Int, value: Double): Color = when {
            levels.isNotEmpty() -> levels.firstOrNull { value in it.range }?.color ?: color
            bands.getOrNull(i)?.let { value !in it } == true -> colors.outOfRange
            else -> color
        }
        val dense = count > DENSE_DAYS
        val path = Path()
        var drawing = false
        values.forEachIndexed { i, value ->
            if (value == null) {
                drawing = false
            } else if (drawing) {
                path.lineTo(x(i), y(value))
            } else {
                path.moveTo(x(i), y(value))
                drawing = true
            }
        }
        val lineColor = if (trend.isNotEmpty()) color.copy(alpha = FAINT_ALPHA) else color
        drawPath(path, lineColor, style = Stroke((if (dense) 1.5 else 2.0).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (trend.isNotEmpty()) {
            val trendPath = Path()
            var started = false
            trend.forEachIndexed { i, value ->
                if (value == null) {
                    started = false
                } else if (started) {
                    trendPath.lineTo(x(i), y(value))
                } else {
                    trendPath.moveTo(x(i), y(value))
                    started = true
                }
            }
            drawPath(trendPath, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val radius = (if (dense) 2.5 else 4.0).dp.toPx()
        values.forEachIndexed { i, value ->
            if (value == null) return@forEachIndexed
            val center = Offset(x(i), y(value))
            drawCircle(ring, radius + 1.5.dp.toPx(), center)
            drawCircle(pointColor(i, value), radius, center)
        }
        selected?.let { i ->
            val value = values.getOrNull(i) ?: return@let
            drawLine(ink, Offset(x(i), TOP.toPx()), Offset(x(i), bottom), 1.5.dp.toPx())
            drawCircle(ring, SELECTED_RADIUS.toPx() + 2.dp.toPx(), Offset(x(i), y(value)))
            drawCircle(ink, SELECTED_RADIUS.toPx(), Offset(x(i), y(value)), style = Stroke(2.dp.toPx()))
        }
    }
}

/** A scale from a little below [min] to a little above [max], on round numbers. */
private fun niceRange(min: Double, max: Double): Pair<Double, Double> {
    val span = (max - min).coerceAtLeast(1.0)
    val step = niceStep(span / 3)
    return floor((min - span * 0.1) / step) * step to ceil((max + span * 0.1) / step) * step
}

private fun ticks(low: Double, high: Double): List<Double> {
    val step = niceStep((high - low) / 3)
    val first = ceil(low / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= high + 1e-9 }.toList()
}

private fun niceStep(raw: Double): Double {
    val magnitude = Math.pow(10.0, floor(Math.log10(raw)))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
}

private val AXIS = 36.dp
private val LABELS = 20.dp
private val TOP = 8.dp
private val SELECTED_RADIUS = 6.dp
private const val BAND_ALPHA = 0.16f
private const val LEVEL_ALPHA = 0.14f
private const val FAINT_ALPHA = 0.35f
private const val DENSE_DAYS = 45
