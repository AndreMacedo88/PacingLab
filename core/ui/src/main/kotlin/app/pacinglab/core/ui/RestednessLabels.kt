package app.pacinglab.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.RestednessEstimate
import app.pacinglab.core.analytics.recovery.RestednessStanding
import app.pacinglab.core.designsystem.chart.DayLabels
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import java.util.Locale
import kotlin.math.roundToInt

/** An expected answer as shown: one decimal on the 1 to 5 scale of the morning question. */
fun formatRestedness(value: Double, locale: Locale): String = String.format(locale, "%.1f", value)

/** The answer nearest an expected value, for its word. */
fun nearestAnswer(value: Double): Int = value.roundToInt().coerceIn(RestednessEstimate.LOWEST.toInt(), RestednessEstimate.HIGHEST.toInt())

/** Where a morning is expected to lie against the athlete's usual answer, with an arrow or a tick. */
@Composable
fun RestednessPill(standing: RestednessStanding, modifier: Modifier = Modifier) {
    when (standing) {
        RestednessStanding.ABOVE -> StatusPill(
            stringResource(R.string.restedness_above),
            chartColors().positive,
            PacingLabIcons.ArrowUp,
            modifier,
        )

        RestednessStanding.USUAL -> StatusPill(
            stringResource(R.string.restedness_usual),
            StatusColors.Good,
            PacingLabIcons.CheckCircle,
            modifier,
        )

        RestednessStanding.BELOW -> StatusPill(
            stringResource(R.string.restedness_below),
            StatusColors.Warning,
            PacingLabIcons.ArrowDown,
            modifier,
        )
    }
}

/**
 * The expected answer over evenly spaced mornings as a line, with the athlete's own answers as rings,
 * on the whole 1 to 5 scale so that the distance between them reads the same every week. Faint lines
 * mark the five answers and a darker hairline the usual one. [labels], such as weekday initials, go under the mornings, the last one
 * marked as today.
 */
@Composable
fun RestednessChart(
    expected: List<Double?>,
    answers: List<Int?>,
    modifier: Modifier = Modifier,
    usual: Double? = null,
    labels: List<String>? = null,
    height: Dp = 40.dp,
) {
    val colors = chartColors()
    val line = colors.series[0]
    val ink = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val description = stringResource(R.string.restedness_chart)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            val low = RestednessEstimate.LOWEST
            val high = RestednessEstimate.HIGHEST
            val inset = 4.dp.toPx()
            val slot = size.width / expected.size
            fun y(v: Double) = (inset + (high - v) / (high - low) * (size.height - 2 * inset)).toFloat()
            fun x(i: Int) = (i + 0.5f) * slot

            // A faint line at each answer, so that the rings read as steps of the question.
            for (answer in low.toInt()..high.toInt()) {
                drawLine(colors.grid, Offset(0f, y(answer.toDouble())), Offset(size.width, y(answer.toDouble())), 1.dp.toPx())
            }
            usual?.let { drawLine(colors.baseline, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }
            for (k in 0 until expected.size - 1) {
                val a = expected[k] ?: continue
                val b = expected[k + 1] ?: continue
                drawLine(line, Offset(x(k), y(a)), Offset(x(k + 1), y(b)), 2.dp.toPx(), cap = StrokeCap.Round)
            }
            expected.forEachIndexed { k, v ->
                if (v == null) return@forEachIndexed
                val radius = if (k == expected.lastIndex) 3.dp.toPx() else 2.5.dp.toPx()
                drawCircle(ring, radius + 1.5.dp.toPx(), Offset(x(k), y(v)))
                drawCircle(line, radius, Offset(x(k), y(v)))
            }
            answers.forEachIndexed { k, answer ->
                if (answer == null || k >= expected.size) return@forEachIndexed
                drawCircle(ink, 4.dp.toPx(), Offset(x(k), y(answer.toDouble())), style = Stroke(1.5.dp.toPx()))
            }
        }
        if (labels != null) DayLabels(labels)
    }
}
