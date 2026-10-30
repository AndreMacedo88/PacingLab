package app.pacinglab.feature.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Chronotype
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepTiming
import app.pacinglab.core.analytics.recovery.SleepWindow
import app.pacinglab.core.analytics.recovery.SocialJetlagLevel
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * When the athlete sleeps on workdays and on free days, from the last four weeks of nights: the
 * chronotype (mid-sleep on free days, corrected for sleep debt) and the social jetlag between the two,
 * with each group's average night drawn on one clock axis.
 */
@Composable
internal fun SleepTimingCard(nights: List<Night>, night: Night, freeDays: Set<DayOfWeek>, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val timing = remember(nights, night.date, freeDays) { SleepTiming.of(nights, night.date, freeDays) }
    SleepCard(stringResource(R.string.timing_title), stringResource(R.string.timing_info), modifier) {
        if (timing == null || !timing.ready) {
            Text(
                stringResource(
                    R.string.timing_learning,
                    SleepTiming.MIN_FREE_NIGHTS,
                    SleepTiming.MIN_WORK_NIGHTS,
                    timing?.free?.nights ?: 0,
                    timing?.work?.nights ?: 0,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SleepCard
        }
        val work = timing.work!!
        val free = timing.free!!
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.timing_chronotype),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(clock(timing.chronotype!!), style = MaterialTheme.typography.headlineSmall.tabularNumbers())
                Text(stringResource(timing.chronotypeKind!!.label()), style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.timing_jetlag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    Formats.hoursMinutes(timing.socialJetlagMinutes!! * MINUTE.toLong()),
                    style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                )
                when (timing.socialJetlag!!) {
                    SocialJetlagLevel.LOW -> StatusPill(
                        stringResource(R.string.timing_jetlag_low),
                        StatusColors.Good,
                        PacingLabIcons.CheckCircle,
                    )

                    SocialJetlagLevel.MODERATE ->
                        StatusPill(stringResource(R.string.timing_jetlag_moderate), StatusColors.Warning, PacingLabIcons.Warning)

                    SocialJetlagLevel.HIGH -> StatusPill(
                        stringResource(R.string.timing_jetlag_high),
                        StatusColors.Serious,
                        PacingLabIcons.Warning,
                    )
                }
            }
        }
        TimingChart(
            rows = listOf(
                stringResource(R.string.timing_workdays) to work,
                stringResource(R.string.timing_free_days) to free,
            ),
            description = stringResource(
                R.string.timing_chart_description,
                clock(work.midpointClock),
                clock(free.midpointClock),
            ),
            locale = locale,
        )
        Text(
            stringResource(
                R.string.timing_basis,
                work.nights,
                free.nights,
                DayOfWeek.entries.filter { it in freeDays }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Each group's average night as a bar from falling asleep to waking, with its middle marked. */
@Composable
private fun TimingChart(rows: List<Pair<String, SleepWindow>>, description: String, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val rowHeight = 28.dp
    // Whole hours around the earliest bedtime and latest waking, counted from noon like the windows.
    val from = rows.minOf { it.second.onset } / 60 * 60
    val to = (rows.maxOf { it.second.wake } + 59) / 60 * 60
    Canvas(modifier.fillMaxWidth().height(rowHeight * rows.size + 18.dp).semantics { contentDescription = description }) {
        val labelWidth = 72.dp.toPx()
        val plotWidth = size.width - labelWidth
        val span = (to - from).coerceAtLeast(60).toFloat()
        fun x(minute: Int) = labelWidth + (minute - from) / span * plotWidth
        val plotBottom = rowHeight.toPx() * rows.size
        var hour = from
        while (hour <= to) {
            drawLine(colors.grid, Offset(x(hour), 0f), Offset(x(hour), plotBottom), 1.dp.toPx())
            val label = measurer.measure("%02d".format(locale, SleepWindow.clock(hour) / 60), style)
            drawText(
                label,
                topLeft = Offset(
                    (x(hour) - label.size.width / 2).coerceIn(0f, size.width - label.size.width),
                    size.height - label.size.height,
                ),
            )
            hour += 120
        }
        rows.forEachIndexed { i, (name, window) ->
            val top = i * rowHeight.toPx()
            val center = top + rowHeight.toPx() / 2
            val label = measurer.measure(name, style)
            drawText(label, topLeft = Offset(0f, center - label.size.height / 2))
            val height = rowHeight.toPx() * 0.5f
            drawRoundRect(
                color = colors.series[0].copy(alpha = BAR_ALPHA),
                topLeft = Offset(x(window.onset), center - height / 2),
                size = Size(x(window.wake) - x(window.onset), height),
                cornerRadius = CornerRadius(height / 2),
            )
            drawCircle(ring, 6.dp.toPx(), Offset(x(window.midpoint), center))
            drawCircle(ink, 4.dp.toPx(), Offset(x(window.midpoint), center))
        }
    }
}

private fun Chronotype.label(): Int = when (this) {
    Chronotype.EARLY -> R.string.timing_early
    Chronotype.INTERMEDIATE -> R.string.timing_intermediate
    Chronotype.LATE -> R.string.timing_late
}

private const val BAR_ALPHA = 0.45f
