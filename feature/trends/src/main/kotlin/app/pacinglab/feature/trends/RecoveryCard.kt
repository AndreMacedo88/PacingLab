package app.pacinglab.feature.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.RecoveryDay
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * HRV and resting heart rate over time, as HRV-guided training reads them: each night as a faint dot,
 * the seven-day average as the line, and the normal range that readiness uses shaded day by day. The
 * line turns to the out-of-range colour while the week sits outside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecoveryCard(days: List<RecoveryDay>, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    var range by rememberSaveable { mutableIntStateOf(TrendRange.THREE_MONTHS.ordinal) }
    val selected = TrendRange.entries[range]
    val first = maxOf(days.first().date, today.minusDays(selected.days - 1L))
    val shown = days.filter { it.date >= first }
    val colors = chartColors()
    val latest = days.last()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = stringResource(R.string.recovery_title), info = stringResource(R.string.recovery_info))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TrendRange.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = range == i,
                        onClick = { range = i },
                        shape = SegmentedButtonDefaults.itemShape(i, TrendRange.entries.size),
                    ) { Text(stringResource(option.label)) }
                }
            }
            Headline(
                name = stringResource(R.string.recovery_hrv),
                value = latest.hrvWeekMs?.let { stringResource(R.string.recovery_hrv_value, it.roundToInt()) },
                range = latest.hrvNormal?.let {
                    stringResource(R.string.recovery_hrv_range, it.start.roundToInt(), it.endInclusive.roundToInt())
                },
            )
            BandTrendChart(
                days = shown.map { it.date },
                line = shown.map { it.hrvWeekMs },
                dots = shown.map { it.hrvMs },
                bands = shown.map { it.hrvNormal },
                color = colors.series[0],
                first = first,
                today = today,
                description = stringResource(R.string.recovery_hrv_description),
                locale = locale,
            )
            Headline(
                name = stringResource(R.string.recovery_resting),
                value = latest.restingWeek?.let { stringResource(R.string.recovery_resting_value, it.roundToInt()) },
                range = latest.restingNormal?.let {
                    stringResource(R.string.recovery_resting_range, it.start.roundToInt(), it.endInclusive.roundToInt())
                },
            )
            BandTrendChart(
                days = shown.map { it.date },
                line = shown.map { it.restingWeek },
                dots = shown.map { it.restingHr },
                bands = shown.map { it.restingNormal },
                color = colors.restingHr,
                first = first,
                today = today,
                description = stringResource(R.string.recovery_resting_description),
                locale = locale,
            )
        }
    }
}

@Composable
private fun Headline(name: String, value: String?, range: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MaterialTheme.typography.labelLarge)
        Text(
            text = listOfNotNull(value, range).joinToString("  ·  ").ifEmpty { stringResource(R.string.recovery_not_enough) },
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A line over days with a band that may change every day, drawn as steps, and faint dots for the daily
 * values behind the line. The line takes [color] where it lies inside the day's band and the
 * out-of-range colour where it does not.
 */
@Composable
private fun BandTrendChart(
    days: List<LocalDate>,
    line: List<Double?>,
    dots: List<Double?>,
    bands: List<ClosedFloatingPointRange<Double>?>,
    color: Color,
    first: LocalDate,
    today: LocalDate,
    description: String,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val colors = chartColors()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val month = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val dayMonth = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    Canvas(modifier.fillMaxWidth().height(CHART_HEIGHT).semantics { contentDescription = description }) {
        val values = (line + dots).filterNotNull() + bands.filterNotNull().flatMap { listOf(it.start, it.endInclusive) }
        if (values.isEmpty()) return@Canvas
        val axis = 18.dp.toPx()
        val plotWidth = size.width - 32.dp.toPx()
        val plotHeight = size.height - axis
        val low = floor(values.min() - 1)
        val high = ceil(values.max() + 1)
        val span = (today.toEpochDay() - first.toEpochDay() + 1).coerceAtLeast(1)
        val slot = plotWidth / span
        fun x(date: LocalDate) = ((date.toEpochDay() - first.toEpochDay()) + 0.5f) * slot
        fun y(v: Double) = ((high - v) / (high - low) * plotHeight).toFloat()

        // Gridlines at round values, about three of them, labelled on the right.
        val step = listOf(1.0, 2.0, 5.0, 10.0, 20.0).first { (high - low) / it <= 4 }
        var tick = ceil(low / step) * step
        while (tick <= high) {
            drawLine(colors.grid, Offset(0f, y(tick)), Offset(plotWidth, y(tick)), 1.dp.toPx())
            val layout = measurer.measure(tick.roundToInt().toString(), labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    plotWidth + 6.dp.toPx(),
                    (y(tick) - layout.size.height / 2).coerceIn(
                        0f,
                        plotHeight - layout.size.height,
                    ),
                ),
            )
            tick += step
        }

        // The band, one step per day, joined into one shape per stretch of days that have it.
        var i = 0
        while (i < days.size) {
            if (bands[i] == null) {
                i++
                continue
            }
            var j = i
            while (j + 1 < days.size && bands[j + 1] != null) j++
            val shape = Path().apply {
                moveTo(x(days[i]) - slot / 2, y(bands[i]!!.endInclusive))
                for (k in i..j) {
                    lineTo(x(days[k]) - slot / 2, y(bands[k]!!.endInclusive))
                    lineTo(x(days[k]) + slot / 2, y(bands[k]!!.endInclusive))
                }
                for (k in j downTo i) {
                    lineTo(x(days[k]) + slot / 2, y(bands[k]!!.start))
                    lineTo(x(days[k]) - slot / 2, y(bands[k]!!.start))
                }
                close()
            }
            drawPath(shape, color.copy(alpha = BAND_ALPHA))
            i = j + 1
        }

        dots.forEachIndexed { k, v -> if (v != null) drawCircle(color.copy(alpha = DOT_ALPHA), 2.dp.toPx(), Offset(x(days[k]), y(v))) }

        fun colorAt(k: Int): Color {
            val band = bands[k] ?: return color
            return if (line[k]!! in band) color else colors.outOfRange
        }
        val stroke = 2.dp.toPx()
        for (k in 0 until days.size - 1) {
            val a = line[k] ?: continue
            val b = line[k + 1] ?: continue
            val from = Offset(x(days[k]), y(a))
            val to = Offset(x(days[k + 1]), y(b))
            val middle = Offset((from.x + to.x) / 2, (from.y + to.y) / 2)
            drawLine(colorAt(k), from, middle, stroke, cap = StrokeCap.Round)
            drawLine(colorAt(k + 1), middle, to, stroke, cap = StrokeCap.Round)
        }

        // Dates along the bottom: Mondays for short spans, months for long ones.
        drawLine(colors.baseline, Offset(0f, plotHeight), Offset(plotWidth, plotHeight), 1.dp.toPx())
        val weekly = span <= WEEKLY_TICKS_UP_TO
        var date = if (weekly) first.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)) else first.withDayOfMonth(1).plusMonths(1)
        var free = 0f
        while (!date.isAfter(today)) {
            val label = when {
                weekly -> dayMonth.format(date)
                date.monthValue == 1 -> date.year.toString()
                else -> month.format(date)
            }
            val layout = measurer.measure(label, labelStyle)
            val left = x(date) - slot / 2
            if (left >= free && left + layout.size.width <= plotWidth) {
                drawLine(colors.baseline, Offset(left, plotHeight), Offset(left, plotHeight + 4.dp.toPx()), 1.dp.toPx())
                drawText(layout, topLeft = Offset(left + 2.dp.toPx(), size.height - layout.size.height))
                free = left + layout.size.width + 8.dp.toPx()
            }
            date = if (weekly) date.plusWeeks(if (span > 3 * 7) 2 else 1) else date.plusMonths(if (span > 200) 2 else 1)
        }
    }
}

private val CHART_HEIGHT = 120.dp
private const val BAND_ALPHA = 0.16f
private const val DOT_ALPHA = 0.45f

/** Up to this many days the axis marks Mondays; beyond it, months. */
private const val WEEKLY_TICKS_UP_TO = 60
