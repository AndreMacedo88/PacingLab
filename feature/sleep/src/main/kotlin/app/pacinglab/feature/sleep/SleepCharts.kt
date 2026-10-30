package app.pacinglab.feature.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepingHeartRate
import app.pacinglab.core.designsystem.theme.ChartColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.model.SleepStageType
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lanes of the stage chart, top to bottom: the further down, the deeper the sleep. */
internal val STAGE_LANES = listOf(SleepStageType.AWAKE, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.DEEP)

internal fun ChartColors.stage(type: SleepStageType): Color = when (type) {
    SleepStageType.DEEP -> deepSleep
    SleepStageType.REM -> remSleep
    SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> awake
    else -> lightSleep
}

private fun SleepStageType.lane(): Int = when (this) {
    SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> 0
    SleepStageType.REM -> 1
    SleepStageType.DEEP -> 3
    else -> 2
}

/**
 * The night's stages as one lane per stage, each labelled with its total, the blocks joined by thin
 * lines where the stage changes. [laneLabels] are in the order of [STAGE_LANES]. With [restlessLabel] and
 * the night's brief awakenings known, a lane under Awake marks each of them, in a lighter awake colour.
 */
@Composable
internal fun StageLanes(
    night: Night,
    laneLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    restlessLabel: String? = null,
) {
    val colors = chartColors()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val brief = night.briefAwakenings?.takeIf { restlessLabel != null }
    val labels = if (brief != null) listOf(laneLabels.first(), restlessLabel!!) + laneLabels.drop(1) else laneLabels
    fun laneOf(type: SleepStageType) = type.lane().let { if (brief != null && it > 0) it + 1 else it }
    Canvas(modifier.fillMaxWidth().height(LANE_HEIGHT * labels.size).semantics { contentDescription = description }) {
        val laneHeight = LANE_HEIGHT.toPx()
        val label = 20.dp.toPx()
        val trackHeight = laneHeight - label - 8.dp.toPx()
        val start = night.start.toEpochMilli()
        val span = (night.end.toEpochMilli() - start).coerceAtLeast(1).toFloat()
        fun x(t: Instant) = (t.toEpochMilli() - start) / span * size.width
        fun trackTop(lane: Int) = lane * laneHeight + label

        labels.forEachIndexed { lane, text ->
            drawText(measurer, text, Offset(0f, lane * laneHeight), labelStyle)
            drawRoundRect(track, Offset(0f, trackTop(lane)), Size(size.width, trackHeight), CornerRadius(trackHeight / 2))
        }
        var previous: Int? = null
        for (stage in night.stages) {
            val lane = laneOf(stage.type)
            val left = x(stage.start)
            val right = maxOf(x(stage.end), left + 2.dp.toPx())
            previous?.takeIf { it != lane }?.let { from ->
                val upper = minOf(from, lane)
                val lower = maxOf(from, lane)
                drawLine(
                    colors.muted.copy(alpha = 0.5f),
                    Offset(left, trackTop(upper) + trackHeight / 2),
                    Offset(left, trackTop(lower) + trackHeight / 2),
                    1.dp.toPx(),
                )
            }
            drawRoundRect(
                color = colors.stage(stage.type),
                topLeft = Offset(left, trackTop(lane)),
                size = Size(right - left, trackHeight),
                cornerRadius = CornerRadius(minOf(trackHeight / 2, (right - left) / 2)),
            )
            previous = lane
        }
        if (brief != null) {
            // Over the lines between stages that cross it, so that only brief awakenings mark this lane.
            drawRoundRect(track, Offset(0f, trackTop(RESTLESS_LANE)), Size(size.width, trackHeight), CornerRadius(trackHeight / 2))
        }
        brief?.forEach { awakening ->
            val left = x(awakening.start)
            val right = maxOf(x(awakening.end), left + 2.dp.toPx())
            drawRoundRect(
                color = colors.awake.copy(alpha = RESTLESS_ALPHA),
                topLeft = Offset(left, trackTop(RESTLESS_LANE)),
                size = Size(right - left, trackHeight),
                cornerRadius = CornerRadius(minOf(trackHeight / 2, (right - left) / 2)),
            )
        }
    }
}

/** The restless lane sits right under Awake. */
private const val RESTLESS_LANE = 1

/** Brief awakenings take the awake colour, lighter, so the two lanes read as related but not the same. */
private const val RESTLESS_ALPHA = 0.55f

/**
 * Where a value falls on a track: the healthy range as a tinted band, the value as a marker. Values
 * beyond the track are drawn at its end.
 */
@Composable
internal fun RangeBar(
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    track: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val height = 10.dp.toPx()
        val top = (size.height - height) / 2
        fun x(v: Double) =
            ((v.coerceIn(track.start, track.endInclusive) - track.start) / (track.endInclusive - track.start) * size.width).toFloat()
        drawRoundRect(trackColor, Offset(0f, top), Size(size.width, height), CornerRadius(height / 2))
        drawRoundRect(
            colors.positive.copy(alpha = 0.4f),
            Offset(x(range.start), top),
            Size(x(range.endInclusive) - x(range.start), height),
            CornerRadius(height / 2),
        )
        val center = Offset(x(value), size.height / 2)
        drawCircle(ring, 8.dp.toPx(), center)
        drawCircle(ink, 6.dp.toPx(), center)
    }
}

/**
 * Heart rate through the night as five-minute averages, with the night's resting heart rate as a
 * reference line and the lowest stretch marked.
 */
@Composable
internal fun SleepingHeartRateChart(
    night: Night,
    sleeping: SleepingHeartRate,
    restingLabel: String?,
    description: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val line = colors.series[1]
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val style = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
        val axis = 18.dp.toPx()
        val plotHeight = size.height - axis
        val resting = night.restingHr
        val values = sleeping.curve.map { it.second } + listOfNotNull(resting)
        val low = values.min() - 3
        val high = values.max() + 3
        val start = night.start.toEpochMilli()
        val span = (night.end.toEpochMilli() - start).coerceAtLeast(1).toFloat()
        fun x(t: Instant) = (t.toEpochMilli() - start) / span * size.width
        fun y(bpm: Double) = ((high - bpm) / (high - low) * plotHeight).toFloat()

        resting?.let {
            drawLine(colors.baseline, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx())
            restingLabel?.let { text ->
                val layout = measurer.measure(text, style)
                drawText(
                    layout,
                    topLeft = Offset(size.width - layout.size.width, (y(it) - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)),
                )
            }
        }
        val path = Path()
        sleeping.curve.forEachIndexed { i, (t, bpm) -> if (i == 0) path.moveTo(x(t), y(bpm)) else path.lineTo(x(t), y(bpm)) }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (lowestAt, lowestBpm) = sleeping.curve.minBy { it.second }
        drawCircle(ring, 5.dp.toPx(), Offset(x(lowestAt), y(lowestBpm)))
        drawCircle(line, 3.5.dp.toPx(), Offset(x(lowestAt), y(lowestBpm)))
        clockLabels(measurer, night, time, style, ::x)
    }
}

/** Bedtime at the left, the wake time at the right and every second hour between, without collisions. */
private fun DrawScope.clockLabels(measurer: TextMeasurer, night: Night, time: DateTimeFormatter, style: TextStyle, x: (Instant) -> Float) {
    val start = measurer.measure(time.format(night.start.atZone(night.zone)), style)
    val end = measurer.measure(time.format(night.end.atZone(night.zone)), style)
    drawText(start, topLeft = Offset(0f, size.height - start.size.height))
    drawText(end, topLeft = Offset(size.width - end.size.width, size.height - end.size.height))
    var free = start.size.width + 8.dp.toPx()
    val limit = size.width - end.size.width - 8.dp.toPx()
    var hour = night.start.atZone(night.zone).withMinute(0).withSecond(0).withNano(0).plusHours(2).toInstant()
    while (hour < night.end) {
        val layout = measurer.measure(time.format(hour.atZone(night.zone)), style)
        val left = x(hour) - layout.size.width / 2
        if (left >= free && left + layout.size.width <= limit) {
            drawText(layout, topLeft = Offset(left, size.height - layout.size.height))
            free = left + layout.size.width + 8.dp.toPx()
        }
        hour = hour.plusSeconds(2 * 3_600L)
    }
}

/**
 * Recent nights as bars from going to bed to getting up on a shared clock axis, one row per night,
 * the selected night in full colour. Lined-up bars mean a regular rhythm.
 */
@Composable
internal fun SleepWindows(nights: List<Night>, selected: LocalDate, description: String, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val day = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val rowHeight = 18.dp

    // Clock minutes counted from noon, so that a night runs left to right without wrapping.
    fun fromNoon(t: Instant, night: Night): Int = (t.atZone(night.zone).toLocalTime().toSecondOfDay() / 60 - NOON + DAY) % DAY
    val from = (nights.minOfOrNull { fromNoon(it.start, it) } ?: 0) / 60 * 60
    val to = ((nights.maxOfOrNull { fromNoon(it.end, it) } ?: DAY) + 59) / 60 * 60
    Canvas(modifier.fillMaxWidth().height(rowHeight * nights.size + 18.dp).semantics { contentDescription = description }) {
        val labelWidth = 48.dp.toPx()
        val plotWidth = size.width - labelWidth
        val span = (to - from).coerceAtLeast(60).toFloat()
        fun x(minute: Int) = labelWidth + (minute - from) / span * plotWidth
        var hour = from
        while (hour <= to) {
            drawLine(colors.grid, Offset(x(hour), 0f), Offset(x(hour), rowHeight.toPx() * nights.size), 1.dp.toPx())
            val layout = measurer.measure("%02d".format(Locale.ROOT, ((hour + NOON) % DAY) / 60), style)
            drawText(
                layout,
                topLeft = Offset(
                    (x(hour) - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width),
                    size.height - layout.size.height,
                ),
            )
            hour += 120
        }
        nights.forEachIndexed { i, night ->
            val top = i * rowHeight.toPx()
            val label = measurer.measure(day.format(night.date), style)
            drawText(label, topLeft = Offset(0f, top + (rowHeight.toPx() - label.size.height) / 2))
            val left = x(fromNoon(night.start, night))
            val right = x(fromNoon(night.end, night)).coerceAtLeast(left + 2.dp.toPx())
            val height = rowHeight.toPx() * 0.6f
            drawRoundRect(
                color = if (night.date == selected) colors.series[0] else colors.muted.copy(alpha = 0.5f),
                topLeft = Offset(left, top + (rowHeight.toPx() - height) / 2),
                size = Size(right - left, height),
                cornerRadius = CornerRadius(height / 2),
            )
        }
    }
}

private val LANE_HEIGHT = 50.dp
private const val NOON = 12 * 60
private const val DAY = 24 * 60
