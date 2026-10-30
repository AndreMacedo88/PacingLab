package app.pacinglab.feature.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.AerobicDecoupling
import app.pacinglab.core.analytics.performance.DecouplingPoint
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Aerobic decoupling of every steady long run: the latest value with its verdict, and a dot per run
 * against the 0-5 % band of good aerobic endurance, shaded in the dots' colour. Dots above the band
 * take the out-of-range orange. The latest run opens on a tap.
 */
@Composable
internal fun DecouplingCard(points: List<DecouplingPoint>, onOpenWorkout: (String) -> Unit, locale: Locale, modifier: Modifier = Modifier) {
    val latest = points.last()
    val good = latest.percent < AerobicDecoupling.WELL_COUPLED
    val date = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    PerformanceCard(stringResource(R.string.decoupling_title), stringResource(R.string.decoupling_info), modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpenWorkout(latest.workoutId) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.decoupling_value, "%.1f".format(locale, latest.percent)),
                style = MaterialTheme.typography.displaySmall.tabularNumbers(),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painterResource(if (good) PacingLabIcons.CheckCircle else PacingLabIcons.Warning),
                        contentDescription = null,
                        tint = if (good) MaterialTheme.colorScheme.primary else chartColors().outOfRange,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(if (good) R.string.decoupling_good else R.string.decoupling_drift),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    text = if (latest.warm) {
                        stringResource(R.string.decoupling_latest_warm, date.format(latest.date), latest.wbgt!!.roundToInt())
                    } else {
                        stringResource(R.string.decoupling_latest, date.format(latest.date))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (points.size > 1) DecouplingChart(points, locale)
        if (points.any { it.warm }) {
            Text(
                stringResource(R.string.decoupling_warm_legend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecouplingChart(points: List<DecouplingPoint>, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val dot = colors.series[0]
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val date = remember(locale) { DateTimeFormatter.ofPattern("MMM yyyy", locale) }
    val first = points.first().date
    val last = points.last().date
    val days = ChronoUnit.DAYS.between(first, last).coerceAtLeast(1).toFloat()
    val high = maxOf(10.0, ceil(points.maxOf { it.percent } / 5) * 5)
    val low = minOf(0.0, points.minOf { it.percent })
    val description = pluralStringResource(R.plurals.decoupling_chart_description, points.size, points.size)
    Canvas(modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = description }) {
        val left = 28.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        fun y(percent: Double) = (top + (high - percent) / (high - low) * (bottom - top)).toFloat()
        drawRect(
            dot.copy(alpha = 0.14f),
            Offset(left, y(AerobicDecoupling.WELL_COUPLED)),
            Size(
                right - left,
                y(0.0) - y(AerobicDecoupling.WELL_COUPLED),
            ),
        )
        for (value in listOf(0.0, AerobicDecoupling.WELL_COUPLED, high)) {
            drawLine(colors.grid, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
            val label = measurer.measure("%.0f".format(locale, value), style)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(value) - label.size.height / 2f))
        }
        for (point in points) {
            val x = left + ChronoUnit.DAYS.between(first, point.date) / days * (right - left)
            val center = Offset(x, y(point.percent))
            val color = if (point.percent < AerobicDecoupling.WELL_COUPLED) dot else colors.outOfRange
            drawCircle(ring, 5.5.dp.toPx(), center)
            // A warm run is drawn hollow: heat adds to its drift, so it is not quite comparable.
            if (point.warm) {
                drawCircle(
                    color,
                    3.4.dp.toPx(),
                    center,
                    style = Stroke(1.6.dp.toPx()),
                )
            } else {
                drawCircle(color, 4.dp.toPx(), center)
            }
        }
        val start = measurer.measure(date.format(first), style)
        val end = measurer.measure(date.format(last), style)
        drawText(start, topLeft = Offset(left, bottom + 4.dp.toPx()))
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 4.dp.toPx()))
    }
}
