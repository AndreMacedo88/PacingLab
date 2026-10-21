package app.pacinglab.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.theme.chartColors

/**
 * A small line over evenly spaced days, for dashboard tiles. Missing days (null) break the line. A
 * [band], such as a personal normal range, is shaded behind it; [baseline] draws a hairline, such as
 * zero for a signed value. The latest value gets a dot. Values are scaled to fit, including the band.
 */
@Composable
fun Sparkline(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = chartColors().series[0],
    band: ClosedFloatingPointRange<Double>? = null,
    baseline: Double? = null,
    height: Dp = 40.dp,
) {
    val colors = chartColors()
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.fillMaxWidth().height(height)) {
        val present = values.filterNotNull()
        if (present.isEmpty()) return@Canvas
        var low = present.min()
        var high = present.max()
        band?.let {
            low = minOf(low, it.start)
            high = maxOf(high, it.endInclusive)
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
        fun y(v: Double) = (inset + (high - v) / (high - low) * usable).toFloat()
        fun x(i: Int) = if (values.size == 1) size.width / 2 else inset + i * (size.width - 2 * inset) / (values.size - 1)
        band?.let {
            drawRect(color.copy(alpha = BAND_ALPHA), Offset(0f, y(it.endInclusive)), Size(size.width, y(it.start) - y(it.endInclusive)))
        }
        baseline?.let { drawLine(colors.baseline, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }
        val path = Path()
        var drawing = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                drawing = false
            } else if (!drawing) {
                path.moveTo(x(i), y(v))
                drawing = true
            } else {
                path.lineTo(x(i), y(v))
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Lone values between gaps would be invisible as a line.
        values.forEachIndexed { i, v ->
            if (v != null && values.getOrNull(i - 1) == null &&
                values.getOrNull(i + 1) == null
            ) {
                drawCircle(color, 2.dp.toPx(), Offset(x(i), y(v)))
            }
        }
        values.indexOfLast { it != null }.takeIf { it >= 0 }?.let { i ->
            val center = Offset(x(i), y(values[i]!!))
            drawCircle(ring, 4.5.dp.toPx(), center)
            drawCircle(color, 3.dp.toPx(), center)
        }
    }
}

/**
 * Small columns over evenly spaced days, the latest in full colour and the others muted. A
 * [reference], such as a sleep need, is drawn as a hairline across. Missing days leave a gap.
 */
@Composable
fun MiniBars(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = chartColors().series[0],
    reference: Double? = null,
    height: Dp = 40.dp,
) {
    val colors = chartColors()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val top = maxOf(values.maxOfOrNull { it ?: 0.0 } ?: 0.0, reference ?: 0.0).takeIf { it > 0 } ?: return@Canvas
        val slot = size.width / values.size
        val width = minOf(slot * 0.6f, 12.dp.toPx())
        val radius = CornerRadius(minOf(4.dp.toPx(), width / 2))
        val last = values.indexOfLast { it != null }
        values.forEachIndexed { i, v ->
            if (v == null || v <= 0) return@forEachIndexed
            val h = (v / top * size.height).toFloat()
            val left = slot * i + (slot - width) / 2
            drawRoundRect(
                if (i ==
                    last
                ) {
                    color
                } else {
                    colors.muted.copy(alpha = MUTED_ALPHA)
                },
                Offset(left, size.height - h),
                Size(width, h),
                radius,
            )
            // Square the base: only the data end is rounded.
            drawRect(
                if (i ==
                    last
                ) {
                    color
                } else {
                    colors.muted.copy(alpha = MUTED_ALPHA)
                },
                Offset(left, size.height - minOf(h, radius.y)),
                Size(width, minOf(h, radius.y)),
            )
        }
        drawLine(colors.baseline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        reference?.let {
            val y = (size.height - it / top * size.height).toFloat()
            drawLine(colors.muted, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), cap = StrokeCap.Butt)
        }
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
private const val MUTED_ALPHA = 0.55f
