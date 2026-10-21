package app.pacinglab.feature.trends

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.designsystem.theme.chartColors
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** The stretch of history the chart shows. */
enum class TrendRange(val days: Int, @param:StringRes val label: Int) {
    SIX_WEEKS(42, R.string.range_six_weeks),
    THREE_MONTHS(91, R.string.range_three_months),
    YEAR(365, R.string.range_year),
}

/**
 * Daily load (bars), fitness and fatigue (lines) and form (area around zero), stacked as small multiples
 * on one time axis so that each keeps its own scale. Touching the chart selects a day: [onSelect] gets
 * its index in [days] while the finger is down and null on release.
 */
@Composable
internal fun FormChart(
    days: List<FormDay>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val months = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val mondays = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val titles = PanelTitles(
        load = stringResource(R.string.chart_load),
        lines = stringResource(R.string.chart_fitness_fatigue),
        form = stringResource(R.string.chart_form),
    )
    val select by rememberUpdatedState(onSelect)
    val count = days.size
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = description }
            .pointerInput(count) {
                fun index(x: Float): Int {
                    val plot = size.width - AXIS_WIDTH.toPx()
                    return if (count <= 1) 0 else (x / plot * (count - 1)).roundToInt().coerceIn(0, count - 1)
                }
                detectTapGestures(
                    onPress = { offset ->
                        select(index(offset.x))
                        tryAwaitRelease()
                        select(null)
                    },
                )
            }
            .pointerInput(count) {
                fun index(x: Float): Int {
                    val plot = size.width - AXIS_WIDTH.toPx()
                    return if (count <= 1) 0 else (x / plot * (count - 1)).roundToInt().coerceIn(0, count - 1)
                }
                detectHorizontalDragGestures(
                    onDragStart = { select(index(it.x)) },
                    onDragEnd = { select(null) },
                    onDragCancel = { select(null) },
                ) { change, _ ->
                    change.consume()
                    select(index(change.position.x))
                }
            },
    ) {
        if (days.isEmpty()) return@Canvas
        val plotWidth = size.width - AXIS_WIDTH.toPx()
        fun x(i: Int): Float = if (count == 1) plotWidth / 2 else i * plotWidth / (count - 1)
        val title = TITLE_HEIGHT.toPx()
        val gap = PANEL_GAP.toPx()
        val load = Panel(top = title, height = LOAD_HEIGHT.toPx())
        val lines = Panel(top = load.bottom + gap + title, height = LINES_HEIGHT.toPx())
        val form = Panel(top = lines.bottom + gap + title, height = FORM_HEIGHT.toPx())
        val hairline = 1.dp.toPx()

        // Daily load: thin columns from a common baseline.
        val loadMax = niceCeiling(days.maxOf { it.load })
        val barWidth = (plotWidth / count * 0.6f).coerceIn(hairline, 4.dp.toPx())
        drawLine(colors.baseline, Offset(0f, load.bottom), Offset(plotWidth, load.bottom), hairline)
        days.forEachIndexed { i, day ->
            if (day.load > 0) {
                val top = load.bottom - (day.load / loadMax * load.height).toFloat()
                drawLine(colors.muted, Offset(x(i), load.bottom), Offset(x(i), top), barWidth, cap = StrokeCap.Butt)
            }
        }
        axisLabel(measurer, format(loadMax), plotWidth, load.top, labelStyle)

        // Fitness and fatigue share one scale.
        val lineMax = niceCeiling(days.maxOf { maxOf(it.fitness, it.fatigue) })
        val step = niceStep(lineMax)
        var tick = 0.0
        while (tick <= lineMax + 1e-9) {
            val y = lines.bottom - (tick / lineMax * lines.height).toFloat()
            drawLine(if (tick == 0.0) colors.baseline else colors.grid, Offset(0f, y), Offset(plotWidth, y), hairline)
            axisLabel(measurer, format(tick), plotWidth, y, labelStyle)
            tick += step
        }
        fun lineY(value: Double) = lines.bottom - (value / lineMax * lines.height).toFloat()
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(polyline(count, ::x) { lineY(days[it].fatigue) }, colors.series[1], style = stroke)
        drawPath(polyline(count, ::x) { lineY(days[it].fitness) }, colors.series[0], style = stroke)

        // Form: blue above zero (fresh), red below (fatigued), around a zero baseline.
        val formExtent = niceCeiling(days.maxOf { abs(it.form) }.coerceAtLeast(MIN_FORM_EXTENT))
        val zero = form.top + form.height / 2
        fun formY(value: Double) = zero - (value / formExtent * form.height / 2).toFloat()
        val formLine = polyline(count, ::x) { formY(days[it].form) }
        val area = polyline(count, ::x) { formY(days[it].form) }.apply {
            lineTo(x(count - 1), zero)
            lineTo(x(0), zero)
            close()
        }
        clipRect(top = form.top, bottom = zero) { drawPath(area, colors.positive.copy(alpha = AREA_ALPHA)) }
        clipRect(top = zero, bottom = form.bottom) { drawPath(area, colors.negative.copy(alpha = AREA_ALPHA)) }
        drawLine(colors.baseline, Offset(0f, zero), Offset(plotWidth, zero), hairline)
        drawPath(formLine, ink, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        axisLabel(measurer, "+" + format(formExtent), plotWidth, form.top, labelStyle)
        axisLabel(measurer, "0", plotWidth, zero, labelStyle)
        axisLabel(measurer, "−" + format(formExtent), plotWidth, form.bottom, labelStyle)

        // Panel titles and dates along the bottom.
        drawText(measurer, titles.load, Offset(0f, load.top - title), labelStyle)
        drawText(measurer, titles.lines, Offset(0f, lines.top - title), labelStyle)
        drawText(measurer, titles.form, Offset(0f, form.top - title), labelStyle)
        var free = 0f
        val labelTop = form.bottom + 4.dp.toPx()
        days.forEachIndexed { i, day ->
            val date = day.date
            val marked = if (count <= WEEKLY_LABELS_UP_TO) date.dayOfWeek == DayOfWeek.MONDAY else date.dayOfMonth == 1
            if (!marked || x(i) < free) return@forEachIndexed
            val text = (if (count <= WEEKLY_LABELS_UP_TO) mondays else months).format(date)
            val layout = measurer.measure(text, labelStyle)
            if (x(i) + layout.size.width > plotWidth) return@forEachIndexed
            drawLine(colors.baseline, Offset(x(i), form.bottom), Offset(x(i), labelTop), hairline)
            drawText(layout, topLeft = Offset(x(i) + 2.dp.toPx(), labelTop))
            free = x(i) + layout.size.width + 8.dp.toPx()
        }

        selected?.takeIf { it in days.indices }?.let { i ->
            val sx = x(i)
            drawLine(ink, Offset(sx, load.top), Offset(sx, form.bottom), hairline)
            marker(Offset(sx, lineY(days[i].fatigue)), colors.series[1], surface)
            marker(Offset(sx, lineY(days[i].fitness)), colors.series[0], surface)
            marker(Offset(sx, formY(days[i].form)), ink, surface)
        }
    }
}

private class PanelTitles(val load: String, val lines: String, val form: String)

private class Panel(val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

private inline fun polyline(count: Int, x: (Int) -> Float, y: (Int) -> Float): Path = Path().apply {
    for (i in 0 until count) if (i == 0) moveTo(x(i), y(i)) else lineTo(x(i), y(i))
}

private fun DrawScope.axisLabel(measurer: TextMeasurer, text: String, plotWidth: Float, y: Float, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val top = (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
    drawText(layout, topLeft = Offset(plotWidth + 6.dp.toPx(), top))
}

private fun DrawScope.marker(center: Offset, color: Color, ring: Color) {
    drawCircle(ring, 5.dp.toPx(), center)
    drawCircle(color, 3.5.dp.toPx(), center)
}

/** The smallest round number (1, 1.5, 2, 2.5, 3, 4, 5, 6, 8 or 10 times a power of ten) at or above [value]. */
internal fun niceCeiling(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val fraction = value / magnitude
    val nice = NICE_STEPS.first { fraction <= it + 1e-9 }
    return nice * magnitude
}

/** A tick spacing that splits 0..[max] into two to five intervals. */
internal fun niceStep(max: Double): Double {
    val magnitude = 10.0.pow(floor(log10(max)))
    return listOf(0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { ceil(max / it - 1e-9) <= 5 }
}

private fun format(value: Double): String = if (value == floor(value)) value.toLong().toString() else "%.1f".format(Locale.ROOT, value)

private val NICE_STEPS = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)
private const val MIN_FORM_EXTENT = 10.0
private const val AREA_ALPHA = 0.28f
private const val WEEKLY_LABELS_UP_TO = 60
private val AXIS_WIDTH = 32.dp
private val TITLE_HEIGHT = 16.dp
private val PANEL_GAP = 10.dp
private val LOAD_HEIGHT = 36.dp
private val LINES_HEIGHT = 132.dp
private val FORM_HEIGHT = 64.dp
private val CHART_HEIGHT = 16.dp * 3 + 36.dp + 132.dp + 64.dp + 10.dp * 2 + 22.dp
