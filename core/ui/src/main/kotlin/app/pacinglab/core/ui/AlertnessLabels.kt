package app.pacinglab.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.alertness.AlertnessCalibration
import app.pacinglab.core.analytics.alertness.AlertnessDay
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.alertness.AlertnessLevel
import app.pacinglab.core.analytics.alertness.ScoredTest
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** A predicted performance as shown: whole percent of a rested day's average. */
fun formatAlertness(value: Double, locale: Locale): String = String.format(locale, "%d %%", value.roundToInt())

/** How a day compares with a rested one, with a tick or an arrow. */
@Composable
fun AlertnessPill(level: AlertnessLevel, modifier: Modifier = Modifier) {
    when (level) {
        AlertnessLevel.ALERT -> StatusPill(
            stringResource(R.string.alertness_level_alert),
            StatusColors.Good,
            PacingLabIcons.CheckCircle,
            modifier,
        )

        AlertnessLevel.REDUCED -> StatusPill(
            stringResource(R.string.alertness_level_reduced),
            StatusColors.Warning,
            PacingLabIcons.ArrowDown,
            modifier,
        )

        AlertnessLevel.LOW -> StatusPill(
            stringResource(R.string.alertness_level_low),
            StatusColors.Serious,
            PacingLabIcons.ArrowDown,
            modifier,
        )
    }
}

/**
 * The score of the day after the night of [date], where in the day the athlete is sharpest, the day's curve
 * and, when [onTest] is given, the reaction test: how far the model has learnt from tests, and a button
 * when one is due. [now] marks the present on today's curve.
 */
@Composable
fun AlertnessSummary(
    history: AlertnessHistory?,
    date: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    onTest: (() -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val day = history?.days?.get(date)
        when {
            history == null -> Muted(stringResource(R.string.alertness_computing))

            day == null -> Muted(stringResource(R.string.alertness_no_nights))

            else -> {
                val now = history.now.takeIf { date == history.today && it >= day.wake && it <= day.end }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatAlertness(day.score, locale), style = MaterialTheme.typography.headlineMedium.tabularNumbers())
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        day.best?.let { (start, end) ->
                            val clock = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }
                            Text(
                                stringResource(R.string.alertness_best, clock.format(start), clock.format(end)),
                                style = MaterialTheme.typography.titleSmall.tabularNumbers(),
                            )
                        }
                        val detail = now?.let { day.at(it) }?.let { stringResource(R.string.alertness_now, formatAlertness(it, locale)) }
                            ?: stringResource(R.string.alertness_of_rested)
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AlertnessPill(day.level)
                    ExperimentalPill()
                }
                AlertnessChart(day, history.testsOn(date, zone), zone, now = now)
                if (day.assumed) Muted(stringResource(R.string.alertness_assumed))
                if (onTest != null) TestRow(history, onTest)
            }
        }
    }
}

@Composable
private fun TestRow(history: AlertnessHistory, onTest: () -> Unit) {
    val calibration = history.calibration
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (calibration.learning) {
            Text(
                pluralStringResource(
                    R.plurals.alertness_learning,
                    AlertnessCalibration.MIN_TESTS,
                    calibration.tests,
                    AlertnessCalibration.MIN_TESTS,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { calibration.tests.toFloat() / AlertnessCalibration.MIN_TESTS },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
        } else {
            Muted(pluralStringResource(R.plurals.alertness_tuned, calibration.tests, calibration.tests))
        }
        if (history.testDue) {
            FilledTonalButton(onClick = onTest) { Text(stringResource(R.string.alertness_take_test)) }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Predicted performance over a waking day on a fixed scale from [LOWEST] to [HIGHEST] percent, so that days
 * compare at a glance: the line, a hairline at 100 (a rested day's average), the best stretch shaded in the
 * line's colour, the athlete's reaction tests as rings, and [now] as a vertical line. Clock times every
 * three hours run underneath.
 */
@Composable
fun AlertnessChart(
    day: AlertnessDay,
    tests: List<ScoredTest>,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    now: Instant? = null,
    height: Dp = 56.dp,
) {
    val colors = chartColors()
    val line = colors.series[0]
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = muted)
    val measurer = rememberTextMeasurer()
    val hours = remember(zone) { DateTimeFormatter.ofPattern("HH").withZone(zone) }
    val clock = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }
    val description = day.best?.let { (start, end) -> stringResource(R.string.alertness_chart, clock.format(start), clock.format(end)) }
        ?: stringResource(R.string.alertness_chart_plain)
    Canvas(modifier.fillMaxWidth().height(height + 18.dp).semantics { contentDescription = description }) {
        val axis = 18.dp.toPx()
        val plot = size.height - axis
        val inset = 4.dp.toPx()
        val start = day.wake.toEpochMilli()
        val span = (day.end.toEpochMilli() - start).coerceAtLeast(1)
        fun x(time: Instant) = ((time.toEpochMilli() - start).toFloat() / span * size.width).coerceIn(0f, size.width)
        fun y(value: Double) = (inset + (HIGHEST - value.coerceIn(LOWEST, HIGHEST)) / (HIGHEST - LOWEST) * (plot - 2 * inset)).toFloat()

        day.best?.let { (from, to) -> drawRect(line.copy(alpha = BAND_ALPHA), Offset(x(from), 0f), Size(x(to) - x(from), plot)) }
        drawLine(colors.baseline, Offset(0f, y(100.0)), Offset(size.width, y(100.0)), 1.dp.toPx())
        val path = Path()
        var previous: Instant? = null
        for (point in day.points) {
            // A gap (a nap) breaks the line.
            if (previous == null || point.time.toEpochMilli() - previous.toEpochMilli() > GAP_MILLIS) {
                path.moveTo(x(point.time), y(point.performance))
            } else {
                path.lineTo(x(point.time), y(point.performance))
            }
            previous = point.time
        }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        now?.let { time ->
            drawLine(muted, Offset(x(time), 0f), Offset(x(time), plot), 1.dp.toPx())
            day.at(time)?.let { value ->
                drawCircle(ring, 4.5.dp.toPx(), Offset(x(time), y(value)))
                drawCircle(line, 3.dp.toPx(), Offset(x(time), y(value)))
            }
        }
        for (test in tests) {
            val measured = test.measured ?: continue
            val middle = test.test.start.plusSeconds(MIDDLE_SECONDS)
            if (middle < day.wake || middle > day.end) continue
            drawCircle(ink, 4.dp.toPx(), Offset(x(middle), y(measured)), style = Stroke(1.5.dp.toPx()))
        }

        var tick = day.wake.atZone(zone).truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (tick.hour % TICK_HOURS != 0) tick = tick.plusHours(1)
        while (!tick.toInstant().isAfter(day.end)) {
            val label = measurer.measure(hours.format(tick), labelStyle)
            val left = (x(tick.toInstant()) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
            drawText(label, topLeft = Offset(left, plot + (axis - label.size.height) / 2f))
            tick = tick.plusHours(TICK_HOURS.toLong())
        }
    }
}

/** The chart's scale, in percent of a rested day's average. */
private const val LOWEST = 70.0
private const val HIGHEST = 110.0
private const val BAND_ALPHA = 0.14f
private const val GAP_MILLIS = 6 * 60_000L
private const val TICK_HOURS = 3
private const val MIDDLE_SECONDS = 90L
