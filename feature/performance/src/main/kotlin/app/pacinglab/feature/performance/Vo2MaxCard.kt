package app.pacinglab.feature.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.Vo2MaxSource
import app.pacinglab.core.data.repository.Vo2MaxHistory
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/** How far back the VO2max chart reaches. */
enum class Vo2MaxRange(val days: Long?, val label: Int) {
    THREE_MONTHS(91, R.string.range_three_months),
    YEAR(365, R.string.range_year),
    ALL(null, R.string.range_all),
}

/**
 * VO2max: today's value from the trend with its uncertainty, and a chart of the trend with a band of
 * one standard deviation and every estimate marked by its source. Tests the athlete entered can be
 * added and removed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Vo2MaxCard(
    history: Vo2MaxHistory,
    today: LocalDate,
    onSaveTest: (LocalDate, Double) -> Unit,
    onDeleteTest: (LocalDate) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    var range by rememberSaveable { mutableStateOf(Vo2MaxRange.THREE_MONTHS) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val trend = history.trend
    PerformanceCard(title = stringResource(R.string.vo2_title), info = stringResource(R.string.vo2_info), modifier = modifier) {
        if (trend == null) {
            Text(stringResource(R.string.vo2_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("%.1f".format(locale, trend.current.value), style = MaterialTheme.typography.displaySmall.tabularNumbers())
                Column {
                    Text(
                        stringResource(R.string.vo2_unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = listOfNotNull(
                            stringResource(R.string.vo2_uncertainty, "%.1f".format(locale, trend.current.sd)),
                            trend.change(30)?.let { stringResource(R.string.vo2_change, signedOneDecimal(it, locale)) },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Vo2MaxRange.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = option == range,
                        onClick = { range = option },
                        shape = SegmentedButtonDefaults.itemShape(i, Vo2MaxRange.entries.size),
                    ) { Text(stringResource(option.label)) }
                }
            }
            Legend(history.points.map { it.source }.toSet())
            Vo2MaxChart(
                history,
                today,
                range,
                locale,
                stringResource(R.string.vo2_chart_description, "%.1f".format(locale, trend.current.value)),
            )
        }
        val tests = history.points.filter { it.source == Vo2MaxSource.TEST }
        if (tests.isNotEmpty()) {
            val date = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
            Text(stringResource(R.string.vo2_tests), style = MaterialTheme.typography.labelLarge)
            for (test in tests.sortedByDescending { it.date }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.vo2_test_row, date.format(test.date), "%.1f".format(locale, test.value)),
                        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteTest(test.date) }) {
                        Icon(painterResource(PacingLabIcons.Delete), stringResource(R.string.vo2_delete_test))
                    }
                }
            }
        }
        TextButton(onClick = { adding = true }) { Text(stringResource(R.string.vo2_add_test)) }
    }
    if (adding) {
        TestDialog(
            today = today,
            onDismiss = { adding = false },
            onSave = { date, value ->
                onSaveTest(date, value)
                adding = false
            },
        )
    }
}

@Composable
private fun Legend(sources: Set<Vo2MaxSource>, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurface
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(stringResource(R.string.vo2_legend_trend)) {
            drawLine(
                colors.series[0],
                Offset(0f, size.height / 2),
                Offset(
                    size.width,
                    size.height / 2,
                ),
                3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        if (Vo2MaxSource.TEST in
            sources
        ) {
            LegendItem(stringResource(R.string.vo2_legend_test)) { diamond(Offset(size.width / 2, size.height / 2), 5.dp.toPx(), ink) }
        }
        if (Vo2MaxSource.FITBIT in
            sources
        ) {
            LegendItem(stringResource(R.string.vo2_legend_fitbit)) { drawCircle(colors.series[1], 4.dp.toPx()) }
        }
        if (Vo2MaxSource.PACINGLAB in sources) {
            LegendItem(stringResource(R.string.vo2_legend_pacinglab)) {
                drawCircle(colors.series[2], 3.5.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, modifier: Modifier = Modifier, width: Dp = 16.dp, mark: DrawScope.() -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(width = width, height = 12.dp), onDraw = mark)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Vo2MaxChart(
    history: Vo2MaxHistory,
    today: LocalDate,
    range: Vo2MaxRange,
    locale: Locale,
    description: String,
    modifier: Modifier = Modifier,
) {
    val trend = history.trend ?: return
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val style = MaterialTheme.typography.labelSmall.copy(color = muted)
    val measurer = rememberTextMeasurer()
    val month = remember(locale) { DateTimeFormatter.ofPattern("MMM", locale) }
    val first = range.days?.let { today.minusDays(it - 1) } ?: trend.days.first().date
    val days = trend.days.filter { it.date >= first }
    val points = history.points.filter { it.date >= first }
    if (days.isEmpty()) return
    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val axis = 18.dp.toPx()
        val labelWidth = 32.dp.toPx()
        val plotWidth = size.width - labelWidth
        val plotHeight = size.height - axis
        val values = days.flatMap { listOf(it.value - it.sd, it.value + it.sd) } + points.map { it.value }
        val low = floor(values.min() - 0.5)
        val high = ceil(values.max() + 0.5)
        val span = (today.toEpochDay() - first.toEpochDay()).coerceAtLeast(1).toFloat()
        fun x(date: LocalDate) = (date.toEpochDay() - first.toEpochDay()) / span * plotWidth
        fun y(v: Double) = ((high - v) / (high - low) * plotHeight).toFloat()

        // Gridlines at whole numbers, about four of them.
        val step = maxOf(1.0, ceil((high - low) / 4))
        var tick = ceil(low / step) * step
        while (tick <= high) {
            drawLine(colors.grid, Offset(0f, y(tick)), Offset(plotWidth, y(tick)), 1.dp.toPx())
            val layout = measurer.measure("%.0f".format(Locale.ROOT, tick), style)
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
        val band = Path().apply {
            days.forEachIndexed { i, d -> if (i == 0) moveTo(x(d.date), y(d.value + d.sd)) else lineTo(x(d.date), y(d.value + d.sd)) }
            days.asReversed().forEach { d -> lineTo(x(d.date), y(d.value - d.sd)) }
            close()
        }
        drawPath(band, colors.series[0].copy(alpha = BAND_ALPHA))
        // Estimates under the trend, so that the line stays on top.
        for (point in points) {
            val center = Offset(x(point.date), y(point.value))
            when (point.source) {
                Vo2MaxSource.PACINGLAB -> drawCircle(
                    colors.series[2].copy(alpha = POINT_ALPHA),
                    3.5.dp.toPx(),
                    center,
                    style = Stroke(1.5.dp.toPx()),
                )

                Vo2MaxSource.FITBIT -> {
                    drawCircle(ring, 5.dp.toPx(), center)
                    drawCircle(colors.series[1].copy(alpha = POINT_ALPHA), 4.dp.toPx(), center)
                }

                Vo2MaxSource.TEST -> Unit
            }
        }
        val line = Path().apply {
            moveTo(x(days[0].date), y(days[0].value))
            for (d in days.drop(1)) lineTo(x(d.date), y(d.value))
        }
        drawPath(line, colors.series[0], style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (point in points.filter { it.source == Vo2MaxSource.TEST }) {
            diamond(Offset(x(point.date), y(point.value)), 6.dp.toPx(), ink, ring)
        }

        // Over long ranges every other month, always including January, which shows the year instead.
        val months = if (span > 400) 2L else 1L
        var free = 0f
        var date = first.withDayOfMonth(1).plusMonths(1)
        while ((date.monthValue - 1) % months != 0L) date = date.plusMonths(1)
        while (!date.isAfter(today)) {
            val label = if (span > 300 && date.monthValue == 1) date.year.toString() else month.format(date)
            val layout = measurer.measure(label, style)
            if (x(date) >= free && x(date) + layout.size.width <= plotWidth) {
                drawLine(colors.baseline, Offset(x(date), plotHeight), Offset(x(date), plotHeight + 4.dp.toPx()), 1.dp.toPx())
                drawText(layout, topLeft = Offset(x(date) + 2.dp.toPx(), size.height - layout.size.height))
                free = x(date) + layout.size.width + 8.dp.toPx()
            }
            date = date.plusMonths(months)
        }
    }
}

private fun DrawScope.diamond(center: Offset, radius: Float, color: Color, ring: Color? = null) {
    fun shape(r: Float) = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r, center.y)
        close()
    }
    ring?.let { drawPath(shape(radius + 2.dp.toPx()), it) }
    drawPath(shape(radius), color)
}

/** Entering a lab or field test: the date it was done and the measured value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestDialog(today: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate, Double) -> Unit) {
    var epochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var text by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val value = text.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value in 20.0..95.0
    val date = LocalDate.ofEpochDay(epochDay)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vo2_add_test)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.vo2_test_explained), style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(date.toString(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.vo2_change_date)) }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) },
                    label = { Text(stringResource(R.string.vo2_title)) },
                    suffix = { Text(stringResource(R.string.vo2_unit)) },
                    isError = text.isNotEmpty() && !valid,
                    supportingText = if (text.isNotEmpty() && !valid) ({ Text(stringResource(R.string.vo2_test_invalid)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onSave(date, it) } }, enabled = valid) { Text(stringResource(R.string.vo2_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vo2_cancel)) } },
    )
    if (picking) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text(stringResource(R.string.vo2_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.vo2_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

private fun signedOneDecimal(value: Double, locale: Locale): String {
    val text = "%.1f".format(locale, kotlin.math.abs(value))
    return when {
        value >= 0.05 -> "+$text"
        value <= -0.05 -> "−$text"
        else -> text
    }
}

private const val BAND_ALPHA = 0.18f
private const val POINT_ALPHA = 0.85f
