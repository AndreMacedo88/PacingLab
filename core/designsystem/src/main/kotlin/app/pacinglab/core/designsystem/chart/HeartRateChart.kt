package app.pacinglab.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.component.ZoneSwatch
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.component.zoneNumber
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.ZoneSet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Heart rate over the time of day, coloured by zone: each stretch of the line takes the colour of the
 * zone it is in, the zones are shaded faintly behind it in the same colours, and dotted lines mark the
 * zone boundaries with their heart rate on the right. Samples more than [MAX_GAP_MILLIS] apart break the
 * line, so the pause between two recorded parts shows as a gap. With [scrubbable], touching the chart
 * reads out the time, heart rate and zone under the finger.
 */
@Composable
fun HeartRateChart(
    heartRate: HeartRateSeries,
    zones: ZoneSet,
    zoneId: ZoneId,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    scrubbable: Boolean = true,
    showLegend: Boolean = true,
) {
    if (heartRate.size < 2) return
    val colors = chartColors()
    val zoneFills = zoneColors(zones.size)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val measurer = rememberTextMeasurer()
    val clock = remember(zoneId) { DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId) }
    val start = heartRate.times.first()
    val end = heartRate.times.last()
    var scrubbed by remember(heartRate) { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (scrubbable) {
            val index = scrubbed
            val text = if (index == null) {
                stringResource(R.string.hr_chart_hint)
            } else {
                val bpm = heartRate.bpm[index]
                val zone = zones.indexOf(bpm)
                stringResource(
                    R.string.hr_chart_readout,
                    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zoneId).format(Instant.ofEpochMilli(heartRate.times[index])),
                    bpm,
                    zoneNumber(zone),
                    stringResource(zones.model.zoneName(zone)),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = if (index == null) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        val axisWidth = 32.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = description }
                .then(
                    if (scrubbable) {
                        Modifier
                            .pointerInput(heartRate) {
                                detectTapGestures(onPress = { offset ->
                                    scrubbed = nearest(heartRate, start, end, offset.x / (size.width - axisWidth.toPx()))
                                    tryAwaitRelease()
                                    scrubbed = null
                                })
                            }
                            .pointerInput(heartRate) {
                                detectHorizontalDragGestures(
                                    onDragStart = { scrubbed = nearest(heartRate, start, end, it.x / (size.width - axisWidth.toPx())) },
                                    onDragEnd = { scrubbed = null },
                                    onDragCancel = { scrubbed = null },
                                ) { change, _ ->
                                    change.consume()
                                    scrubbed = nearest(heartRate, start, end, change.position.x / (size.width - axisWidth.toPx()))
                                }
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val plotWidth = size.width - axisWidth.toPx()
            val axisHeight = 18.dp.toPx()
            val plotHeight = size.height - axisHeight
            var low = heartRate.bpm.min().toDouble()
            var high = heartRate.bpm.max().toDouble()
            // Reach for the nearest boundary just outside the data, so that a session in one zone still
            // shows how far it sat from the next.
            val floors = zones.zones.drop(1).map { it.lowerBpm.toDouble() }
            floors.firstOrNull { it > high && it - high <= BOUNDARY_REACH }?.let { high = it }
            floors.lastOrNull { it < low && low - it <= BOUNDARY_REACH }?.let { low = it }
            low = floor(low - PADDING_BPM)
            high = ceil(high + PADDING_BPM)
            fun x(t: Long) = ((t - start).toDouble() / (end - start).coerceAtLeast(1) * plotWidth).toFloat()
            fun y(v: Double) = ((high - v) / (high - low) * plotHeight).toFloat()

            // Faint zone bands, then the boundaries as dotted lines with their heart rate.
            zones.zones.forEachIndexed { i, zone ->
                val from = if (i == 0) low else maxOf(zone.lowerBpm.toDouble(), low)
                val to = minOf(zones.upperBpm(i)?.toDouble() ?: high, high)
                if (to > from) drawRect(zoneFills[i].copy(alpha = ZONE_BAND_ALPHA), Offset(0f, y(to)), Size(plotWidth, y(from) - y(to)))
            }
            val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
            // Labels from the top down; one that would overlap the label above it is left out.
            var labelsFree = 0f
            for (floor in floors.reversed()) {
                if (floor <= low || floor >= high) continue
                drawLine(colors.baseline, Offset(0f, y(floor)), Offset(plotWidth, y(floor)), 1.dp.toPx(), pathEffect = dots)
                val layout = measurer.measure(floor.toInt().toString(), labelStyle)
                val top = (y(floor) - layout.size.height / 2).coerceIn(0f, plotHeight - layout.size.height)
                if (top >= labelsFree) {
                    drawText(layout, topLeft = Offset(plotWidth + 6.dp.toPx(), top))
                    labelsFree = top + layout.size.height
                }
            }

            // The line, one path per zone so that each stretch takes its zone's colour.
            val paths = List(zones.size) { Path() }
            var previous: Offset? = null
            var previousZone = -1
            for (k in 0 until heartRate.size) {
                val point = Offset(x(heartRate.times[k]), y(heartRate.bpm[k].toDouble()))
                val zone = zones.indexOf(heartRate.bpm[k])
                val joined = k > 0 && heartRate.times[k] - heartRate.times[k - 1] <= MAX_GAP_MILLIS
                if (!joined || previous == null) {
                    paths[zone].moveTo(point.x, point.y)
                } else {
                    if (zone != previousZone) paths[zone].moveTo(previous.x, previous.y)
                    paths[zone].lineTo(point.x, point.y)
                }
                previous = point
                previousZone = zone
            }
            paths.forEachIndexed { i, path ->
                drawPath(path, zoneFills[i], style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Clock times along the bottom.
            drawLine(colors.baseline, Offset(0f, plotHeight), Offset(plotWidth, plotHeight), 1.dp.toPx())
            val step = TICK_MINUTES.firstOrNull { (end - start) / (it * MINUTE) <= MAX_TICKS } ?: TICK_MINUTES.last()
            var tick = (start / (step * MINUTE) + 1) * step * MINUTE
            var free = 0f
            while (tick < end) {
                val layout = measurer.measure(clock.format(Instant.ofEpochMilli(tick)), labelStyle)
                val left = x(tick) - layout.size.width / 2
                if (left >= free && left + layout.size.width <= plotWidth) {
                    drawLine(colors.baseline, Offset(x(tick), plotHeight), Offset(x(tick), plotHeight + 4.dp.toPx()), 1.dp.toPx())
                    drawText(layout, topLeft = Offset(left, size.height - layout.size.height))
                    free = left + layout.size.width + 8.dp.toPx()
                }
                tick += step * MINUTE
            }

            scrubbed?.let { index ->
                val cx = x(heartRate.times[index])
                drawLine(muted, Offset(cx, 0f), Offset(cx, plotHeight), 1.dp.toPx())
                val center = Offset(cx, y(heartRate.bpm[index].toDouble()))
                drawCircle(colors.grid, 5.dp.toPx(), center)
                drawCircle(zoneFills[zones.indexOf(heartRate.bpm[index])], 3.5.dp.toPx(), center)
            }
        }
        if (showLegend) ZoneLegend(zones)
    }
}

/** The zones with their colours, easiest first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneLegend(zones: ZoneSet, modifier: Modifier = Modifier) {
    val fills = zoneColors(zones.size)
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        zones.zones.indices.forEach { i ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ZoneSwatch(fills[i])
                Text(
                    text = if (zones.size == 5) zoneNumber(i) else stringResource(zones.model.zoneName(i)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Index of the sample nearest to [fraction] of the way from [start] to [end]. */
private fun nearest(series: HeartRateSeries, start: Long, end: Long, fraction: Float): Int {
    val t = start + ((end - start) * fraction.coerceIn(0f, 1f)).toLong()
    val found = series.times.binarySearch(t)
    if (found >= 0) return found
    val after = -found - 1
    return when {
        after <= 0 -> 0
        after >= series.size -> series.size - 1
        t - series.times[after - 1] <= series.times[after] - t -> after - 1
        else -> after
    }
}

/** Samples further apart than this break the line. */
private const val MAX_GAP_MILLIS = 30_000L
private const val MINUTE = 60_000L
private const val MAX_TICKS = 5
private val TICK_MINUTES = listOf(1L, 2L, 5L, 10L, 15L, 30L, 60L, 120L, 240L)
private const val PADDING_BPM = 4.0
private const val BOUNDARY_REACH = 25.0
private const val ZONE_BAND_ALPHA = 0.08f
