package app.pacinglab.feature.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.RunningWeek
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Running distance week by week against the athlete's tolerance (by default 30 % over the four weeks before): this
 * week's distance with its status, and the last weeks as grey bars (blue is a zone on this screen) with
 * the tolerance as a tick above each; a week over its tolerance takes the out-of-range orange.
 */
@Composable
internal fun RunningVolumeCard(weeks: List<RunningWeek>, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    val now = weeks.first()
    TrendCard(title = stringResource(R.string.running_title), info = stringResource(R.string.running_info), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.running_this_week), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.running_km, "%.1f".format(locale, now.km)),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                )
                Text(
                    text =
                    now.toleranceKm?.let { tolerance ->
                        val usual = now.usualKm!!
                        stringResource(
                            R.string.running_tolerance,
                            "%.1f".format(locale, tolerance),
                            "%.1f".format(locale, usual),
                            ((tolerance / usual - 1) * 100).roundToInt(),
                        )
                    }
                        ?: stringResource(R.string.running_no_tolerance),
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (now.toleranceKm != null) {
                if (now.above) {
                    StatusPill(stringResource(R.string.running_above), StatusColors.Warning, PacingLabIcons.ArrowUp)
                } else {
                    StatusPill(stringResource(R.string.running_within), StatusColors.Good, PacingLabIcons.CheckCircle)
                }
            }
        }
        RunningChart(weeks.reversed(), today, locale)
    }
}

@Composable
private fun RunningChart(weeks: List<RunningWeek>, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val date = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val high = (weeks.map { it.km } + weeks.mapNotNull { it.toleranceKm }).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val description = pluralStringResource(R.plurals.running_chart_description, weeks.size, weeks.size)
    Canvas(modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
        val bottom = size.height - 18.dp.toPx()
        val slot = size.width / weeks.size
        val barWidth = slot * 0.55f
        fun y(km: Double) = (bottom - km / high * (bottom - 4.dp.toPx())).toFloat()
        drawLine(colors.baseline, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        weeks.forEachIndexed { i, week ->
            val left = i * slot + (slot - barWidth) / 2
            if (week.km > 0) {
                drawRoundRect(
                    if (week.above) colors.outOfRange else colors.muted,
                    Offset(left, y(week.km)),
                    Size(barWidth, bottom - y(week.km)),
                    CornerRadius(3.dp.toPx()),
                )
            }
            week.toleranceKm?.let { tolerance ->
                drawLine(ink, Offset(left - 2.dp.toPx(), y(tolerance)), Offset(left + barWidth + 2.dp.toPx(), y(tolerance)), 2.dp.toPx())
            }
        }
        val first = measurer.measure(date.format(weeks.first().weekStart), style)
        val last = measurer.measure(if (weeks.last().weekStart.plusWeeks(1) > today) "" else date.format(weeks.last().weekStart), style)
        drawText(first, topLeft = Offset(0f, bottom + 4.dp.toPx()))
        drawText(last, topLeft = Offset(size.width - last.size.width, bottom + 4.dp.toPx()))
    }
}
