package app.pacinglab.feature.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.designsystem.theme.ChartColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.model.SleepStageType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows of the hypnogram, top to bottom: the further down, the deeper the sleep. */
internal val HYPNOGRAM_ROWS = listOf(SleepStageType.AWAKE, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.DEEP)

internal fun ChartColors.stage(type: SleepStageType): Color = when (type) {
    SleepStageType.DEEP -> deepSleep
    SleepStageType.REM -> remSleep
    SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> awake
    else -> lightSleep
}

private fun SleepStageType.row(): Int = when (this) {
    SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> 0
    SleepStageType.REM -> 1
    SleepStageType.DEEP -> 3
    else -> 2
}

/**
 * The night's stages over time: one row per stage, awake at the top and deep sleep at the bottom, with
 * thin connectors where the stage changes and clock times along the bottom.
 */
@Composable
internal fun Hypnogram(night: Night, rowLabels: List<String>, description: String, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    Canvas(modifier.fillMaxWidth().height(HYPNOGRAM_HEIGHT).semantics { contentDescription = description }) {
        val labelWidth = rowLabels.maxOf { measurer.measure(it, style).size.width } + 8.dp.toPx()
        val axis = 18.dp.toPx()
        val plotWidth = size.width - labelWidth
        val rowHeight = (size.height - axis) / HYPNOGRAM_ROWS.size
        val start = night.start.toEpochMilli()
        val span = (night.end.toEpochMilli() - start).coerceAtLeast(1)
        fun x(t: Instant) = labelWidth + (t.toEpochMilli() - start).toFloat() / span * plotWidth

        rowLabels.forEachIndexed { i, label ->
            val layout = measurer.measure(label, style)
            drawText(layout, topLeft = Offset(0f, i * rowHeight + (rowHeight - layout.size.height) / 2))
            drawLine(colors.grid, Offset(labelWidth, (i + 1) * rowHeight), Offset(size.width, (i + 1) * rowHeight), 1.dp.toPx())
        }
        val barHeight = rowHeight * 0.62f
        var previousRow: Int? = null
        for (stage in night.stages) {
            val row = stage.type.row()
            val left = x(stage.start)
            val right = maxOf(x(stage.end), left + 1.dp.toPx())
            val top = row * rowHeight + (rowHeight - barHeight) / 2
            previousRow?.takeIf { it != row }?.let { from ->
                drawLine(
                    colors.muted.copy(alpha = 0.6f),
                    Offset(left, from * rowHeight + rowHeight / 2),
                    Offset(left, row * rowHeight + rowHeight / 2),
                    1.dp.toPx(),
                )
            }
            drawRoundRect(colors.stage(stage.type), Offset(left, top), Size(right - left, barHeight), CornerRadius(2.dp.toPx()))
            previousRow = row
        }

        // Start, end and whole hours in between, skipping labels that would collide.
        val labels = buildList {
            add(night.start)
            var hour = night.start.atZone(night.zone).withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
            while (hour < night.end.minus(Duration.ofMinutes(40))) {
                add(hour)
                hour = hour.plus(Duration.ofHours(1))
            }
            add(night.end)
        }
        var free = 0f
        labels.forEachIndexed { i, t ->
            val text = time.format(t.atZone(night.zone))
            val layout = measurer.measure(text, style)
            val center = x(t)
            val left = when (i) {
                0 -> labelWidth
                labels.lastIndex -> size.width - layout.size.width
                else -> center - layout.size.width / 2
            }
            if (left < free ||
                (i != labels.lastIndex && left + layout.size.width > size.width - layout.size.width - 8.dp.toPx())
            ) {
                return@forEachIndexed
            }
            drawText(layout, topLeft = Offset(left, size.height - layout.size.height))
            free = left + layout.size.width + 8.dp.toPx()
        }
    }
}

/**
 * Recent nights as bars from falling asleep to waking on a shared clock axis, newest at the bottom.
 * Lined-up bars mean a regular rhythm. [selected] is drawn in full colour; tapping a row selects it.
 */
@Composable
internal fun SleepSchedule(
    nights: List<Night>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    description: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall.copy(color = ink)
    val selectedStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val day = remember(locale) { DateTimeFormatter.ofPattern("EEE d", locale) }
    val select by rememberUpdatedState(onSelect)
    val rowHeight = 22.dp

    // Clock minutes counted from noon, so that a night runs left to right without wrapping.
    fun minuteFromNoon(t: Instant, night: Night): Int {
        val local = t.atZone(night.zone).toLocalTime().toSecondOfDay() / 60
        return (local - NOON + DAY) % DAY
    }
    val from = (nights.minOfOrNull { minuteFromNoon(it.start, it) } ?: 0) / 60 * 60
    val to = ((nights.maxOfOrNull { minuteFromNoon(it.end, it) } ?: DAY) + 59) / 60 * 60
    Canvas(
        modifier
            .fillMaxWidth()
            .height(rowHeight * nights.size + 20.dp)
            .semantics { contentDescription = description }
            .pointerInput(nights) {
                detectTapGestures { offset ->
                    val index = (offset.y / rowHeight.toPx()).toInt()
                    nights.getOrNull(index)?.let { select(it.date) }
                }
            },
    ) {
        val labelWidth = 52.dp.toPx()
        val plotWidth = size.width - labelWidth
        val span = (to - from).coerceAtLeast(60).toFloat()
        fun x(minute: Int) = labelWidth + (minute - from) / span * plotWidth
        // Hour grid every two hours.
        var hour = from
        while (hour <= to) {
            drawLine(colors.grid, Offset(x(hour), 0f), Offset(x(hour), rowHeight.toPx() * nights.size), 1.dp.toPx())
            val clock = ((hour + NOON) % DAY) / 60
            axisLabel(measurer, "%02d".format(Locale.ROOT, clock), x(hour), size.height, style)
            hour += 120
        }
        nights.forEachIndexed { i, night ->
            val top = i * rowHeight.toPx() + rowHeight.toPx() * 0.2f
            val height = rowHeight.toPx() * 0.6f
            val isSelected = night.date == selected
            val label = measurer.measure(day.format(night.date), if (isSelected) selectedStyle else style)
            drawText(label, topLeft = Offset(0f, i * rowHeight.toPx() + (rowHeight.toPx() - label.size.height) / 2))
            val left = x(minuteFromNoon(night.start, night))
            val right = x(minuteFromNoon(night.end, night)).coerceAtLeast(left + 2.dp.toPx())
            drawRoundRect(
                color = if (isSelected) colors.series[0] else colors.muted.copy(alpha = 0.5f),
                topLeft = Offset(left, top),
                size = Size(right - left, height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.axisLabel(measurer: TextMeasurer, text: String, center: Float, bottom: Float, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val left = (center - layout.size.width / 2).coerceIn(0f, size.width - layout.size.width)
    drawText(layout, topLeft = Offset(left, bottom - layout.size.height))
}

private val HYPNOGRAM_HEIGHT = 150.dp
private const val NOON = 12 * 60
private const val DAY = 24 * 60
