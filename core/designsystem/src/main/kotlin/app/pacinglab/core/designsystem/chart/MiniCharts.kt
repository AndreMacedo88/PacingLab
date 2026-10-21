package app.pacinglab.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.theme.chartColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/**
 * A small line over evenly spaced days, for dashboard tiles and metric rows.
 *
 * A range, such as a personal normal range or a target, is shaded behind the line in the line's own
 * colour: one [band] for every day, or one per day in [bands], drawn as steps because such a range moves
 * from day to day. A value inside its range, and the half of the line next to it, takes [color]; a value
 * outside takes the out-of-range colour of [chartColors], so that a departure stands out at a glance.
 * Days without a range always take [color]. A range may be open at one end (an infinite bound), such as
 * "at least the sleep need"; it then reaches the edge of the chart. Missing days (null) break the line. [labels], such as
 * weekday initials, go under the days, the last one marked as today. [baseline] draws a hairline, such
 * as zero for a signed value. Values are scaled to fit, ranges included.
 */
@Composable
fun Sparkline(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = chartColors().series[0],
    band: ClosedFloatingPointRange<Double>? = null,
    bands: List<ClosedFloatingPointRange<Double>?>? = null,
    baseline: Double? = null,
    labels: List<String>? = null,
    height: Dp = 40.dp,
) {
    val colors = chartColors()
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val present = values.filterNotNull()
            if (present.isEmpty()) return@Canvas
            fun rangeAt(i: Int) = if (bands != null) bands.getOrNull(i) else band
            var low = present.min()
            var high = present.max()
            for (i in values.indices) {
                rangeAt(i)?.let {
                    if (it.start.isFinite()) low = minOf(low, it.start)
                    if (it.endInclusive.isFinite()) high = maxOf(high, it.endInclusive)
                }
            }
            baseline?.let {
                low = minOf(low, it)
                high = maxOf(high, it)
            }
            if (high - low < 1e-9) {
                low -= 1
                high += 1
            }
            val inset = 4.dp.toPx()
            val usable = size.height - 2 * inset
            val slot = size.width / values.size
            fun y(v: Double) = (inset + (high - v) / (high - low) * usable).toFloat()
            fun x(i: Int) = (i + 0.5f) * slot

            // Open ends reach the edge of the chart.
            fun top(range: ClosedFloatingPointRange<Double>) = if (range.endInclusive.isFinite()) y(range.endInclusive) else 0f
            fun bottom(range: ClosedFloatingPointRange<Double>) = if (range.start.isFinite()) y(range.start) else size.height

            // Ranges as one shape, so that neighbouring days join without seams.
            var i = 0
            while (i < values.size) {
                if (rangeAt(i) == null) {
                    i++
                    continue
                }
                var j = i
                while (j + 1 < values.size && rangeAt(j + 1) != null) j++
                val left = if (bands == null) 0f else i * slot
                val right = if (bands == null) size.width else (j + 1) * slot
                val shape = Path().apply {
                    moveTo(left, top(rangeAt(i)!!))
                    for (k in i..j) {
                        val upper = top(rangeAt(k)!!)
                        lineTo(if (k == i) left else k * slot, upper)
                        lineTo(if (k == j) right else (k + 1) * slot, upper)
                    }
                    for (k in j downTo i) {
                        val lower = bottom(rangeAt(k)!!)
                        lineTo(if (k == j) right else (k + 1) * slot, lower)
                        lineTo(if (k == i) left else k * slot, lower)
                    }
                    close()
                }
                drawPath(shape, color.copy(alpha = BAND_ALPHA))
                i = j + 1
            }
            baseline?.let { drawLine(colors.baseline, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }

            fun colorAt(k: Int): Color {
                val range = rangeAt(k) ?: return color
                return if (values[k]!! in range) color else colors.outOfRange
            }
            val stroke = 2.dp.toPx()
            for (k in 0 until values.size - 1) {
                val a = values[k] ?: continue
                val b = values[k + 1] ?: continue
                val from = Offset(x(k), y(a))
                val to = Offset(x(k + 1), y(b))
                val middle = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
                drawLine(colorAt(k), from, middle, stroke, cap = StrokeCap.Round)
                drawLine(colorAt(k + 1), middle, to, stroke, cap = StrokeCap.Round)
            }
            val last = values.indexOfLast { it != null }
            val dots = values.size <= MAX_DOTTED_DAYS
            values.forEachIndexed { k, v ->
                if (v == null) return@forEachIndexed
                val lone = values.getOrNull(k - 1) == null && values.getOrNull(k + 1) == null
                if (!dots && !lone && k != last) return@forEachIndexed
                val center = Offset(x(k), y(v))
                val radius = if (k == last) 3.dp.toPx() else 2.5.dp.toPx()
                drawCircle(ring, radius + 1.5.dp.toPx(), center)
                drawCircle(colorAt(k), radius, center)
            }
        }
        if (labels != null) DayLabels(labels)
    }
}

/** A stretch of values with a meaning of its own, such as "ready", and its colour. */
data class ChartLevel(val range: ClosedFloatingPointRange<Double>, val color: Color, val label: String)

/**
 * A small line over evenly spaced days on a fixed scale divided into [levels]: each level is shaded in
 * its colour and named at the right edge, and the line and dots take the colour of the level each value
 * falls in, the way [Sparkline] colours values inside and outside a range. [labels] go under the days.
 */
@Composable
fun LevelSparkline(
    values: List<Double?>,
    levels: List<ChartLevel>,
    scale: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    height: Dp = 72.dp,
) {
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val low = scale.start
            val high = scale.endInclusive
            val slot = size.width / values.size
            fun y(v: Double) = ((high - v.coerceIn(low, high)) / (high - low) * size.height).toFloat()
            fun x(i: Int) = (i + 0.5f) * slot
            fun colorOf(v: Double) = (
                levels.firstOrNull { v in it.range }
                    ?: levels.minBy { minOf(abs(v - it.range.start), abs(v - it.range.endInclusive)) }
                ).color
            for (level in levels) {
                val top = y(level.range.endInclusive)
                val bottom = y(level.range.start)
                if (bottom - top <= 0f) continue
                drawRect(level.color.copy(alpha = LEVEL_ALPHA), Offset(0f, top), Size(size.width, bottom - top))
                val layout = measurer.measure(level.label, textStyle)
                if (layout.size.height <= bottom - top) {
                    drawText(
                        layout,
                        topLeft = Offset(size.width - layout.size.width - 4.dp.toPx(), (top + bottom - layout.size.height) / 2),
                    )
                }
            }
            val stroke = 2.dp.toPx()
            for (k in 0 until values.size - 1) {
                val a = values[k] ?: continue
                val b = values[k + 1] ?: continue
                val from = Offset(x(k), y(a))
                val to = Offset(x(k + 1), y(b))
                val middle = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
                drawLine(colorOf(a), from, middle, stroke, cap = StrokeCap.Round)
                drawLine(colorOf(b), middle, to, stroke, cap = StrokeCap.Round)
            }
            val last = values.indexOfLast { it != null }
            values.forEachIndexed { k, v ->
                if (v == null) return@forEachIndexed
                val center = Offset(x(k), y(v))
                val radius = if (k == last) 3.5.dp.toPx() else 2.5.dp.toPx()
                drawCircle(ring, radius + 1.5.dp.toPx(), center)
                drawCircle(colorOf(v), radius, center)
            }
        }
        if (labels != null) DayLabels(labels)
    }
}

/** Labels under evenly spaced days, the one at [today] marked. */
@Composable
private fun DayLabels(labels: List<String>, modifier: Modifier = Modifier, today: Int = labels.lastIndex) {
    Row(modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val today = i == today
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = if (today) {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                            .padding(horizontal = 5.dp)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * Initials of the weekdays of [dates] in [locale] ("M", "T", "W"), for [Sparkline] and [MiniBars] labels.
 */
fun weekdayInitials(dates: List<LocalDate>, locale: Locale): List<String> =
    dates.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale) }

/**
 * Small columns over evenly spaced days, the latest in full colour and the others muted. A
 * [reference], such as a sleep need, is drawn as a hairline across. Missing days leave a gap. [labels]
 * go under the days, as for [Sparkline], with the one at [today] marked (the last by default).
 */
@Composable
fun MiniBars(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = chartColors().series[0],
    reference: Double? = null,
    labels: List<String>? = null,
    today: Int? = null,
    height: Dp = 40.dp,
) {
    val colors = chartColors()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val top = maxOf(values.maxOfOrNull { it ?: 0.0 } ?: 0.0, reference ?: 0.0).takeIf { it > 0 } ?: return@Canvas
            val slot = size.width / values.size
            val width = minOf(slot * 0.6f, 12.dp.toPx())
            val radius = CornerRadius(minOf(4.dp.toPx(), width / 2))
            val last = values.indexOfLast { it != null }
            values.forEachIndexed { i, v ->
                if (v == null || v <= 0) return@forEachIndexed
                val h = (v / top * size.height).toFloat()
                val left = slot * i + (slot - width) / 2
                val fill = if (i == last) color else colors.muted.copy(alpha = MUTED_ALPHA)
                drawRoundRect(fill, Offset(left, size.height - h), Size(width, h), radius)
                // Square the base: only the data end is rounded.
                drawRect(fill, Offset(left, size.height - minOf(h, radius.y)), Size(width, minOf(h, radius.y)))
            }
            drawLine(colors.baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            reference?.let {
                val y = (size.height - it / top * size.height).toFloat()
                drawLine(colors.muted, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), cap = StrokeCap.Butt)
            }
        }
        if (labels != null) DayLabels(labels, today = today ?: labels.lastIndex)
    }
}

/** One bar split into segments proportional to [weights], with a small gap between them. */
@Composable
fun SegmentBar(weights: List<Double>, colors: List<Color>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val total = weights.sum()
    Row(modifier = modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total <= 0) {
            Spacer(Modifier.weight(1f).height(height).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)))
        } else {
            weights.forEachIndexed { i, w ->
                if (w > 0) Spacer(Modifier.weight(w.toFloat()).height(height).background(colors[i], RoundedCornerShape(2.dp)))
            }
        }
    }
}

private const val BAND_ALPHA = 0.16f
private const val LEVEL_ALPHA = 0.14f

/** Up to this many days every value gets a dot; longer spans only mark the latest. */
private const val MAX_DOTTED_DAYS = 14
private const val MUTED_ALPHA = 0.55f
