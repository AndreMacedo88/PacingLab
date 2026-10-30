package app.pacinglab.feature.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.CalendarDay
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.ZoneModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A month of training as a grid: a dot on each day with training, its area growing with the day's load
 * and its colour the day's hardest meaningful intensity (the zone colours of the intensity focus), grey
 * without heart rate. A tap on a day opens its main session; arrows step through the months.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainingCalendarCard(
    days: Map<LocalDate, CalendarDay>,
    today: LocalDate,
    locale: Locale,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by rememberSaveable { mutableLongStateOf(0L) }
    val month = YearMonth.from(today).minusMonths(shown)
    val title = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val inMonth = days.filterKeys { YearMonth.from(it) == month }
    val maxLoad = days.filterKeys { it > today.minusMonths(6) }.values.maxOfOrNull { it.load }?.takeIf { it > 0 } ?: 1.0
    val zones = zoneColors(3)
    val none = MaterialTheme.colorScheme.outline
    TrendCard(title = stringResource(R.string.calendar_title), info = stringResource(R.string.calendar_info), modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shown++ }, enabled = days.keys.any { YearMonth.from(it) < month }) {
                Icon(painterResource(PacingLabIcons.ChevronLeft), stringResource(R.string.calendar_previous))
            }
            Text(
                title.format(month).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { shown-- }, enabled = shown > 0) {
                Icon(painterResource(PacingLabIcons.ChevronRight), stringResource(R.string.calendar_next))
            }
        }
        val firstMonday = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Row(Modifier.fillMaxWidth()) {
            for (initial in weekdayInitials((0L until 7L).map { firstMonday.plusDays(it) }, locale)) {
                Text(
                    initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        var monday = firstMonday
        while (monday <= month.atEndOfMonth()) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0L until 7L) {
                    val date = monday.plusDays(d)
                    val day = days[date]
                    val color = day?.intensity?.let { zones[it.ordinal] } ?: none
                    val description = day?.let {
                        stringResource(
                            R.string.calendar_day_description,
                            dayFormat.format(date),
                            it.load.roundToInt(),
                            it.intensity?.let { domain -> stringResource(ZoneModel.POLARIZED_THREE.zoneName(domain.ordinal)) }
                                ?: stringResource(R.string.calendar_no_heart_rate),
                        )
                    } ?: dayFormat.format(date)
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        load = day?.load,
                        share = day?.let { sqrt(it.load / maxLoad).coerceIn(0.0, 1.0) },
                        color = color,
                        description = description,
                        onClick = day?.workoutIds?.firstOrNull()?.let { id -> { onOpenSession(id) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            monday = monday.plusWeeks(1)
        }
        val sessions = inMonth.values.sumOf { it.workoutIds.size }
        Text(
            text = pluralStringResource(
                R.plurals.calendar_month_summary,
                sessions,
                sessions,
                Formats.hoursMinutes(inMonth.values.sumOf { it.millis }),
                inMonth.values.sumOf { it.load }.roundToInt(),
            ),
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 3) LegendDot(zones[i], stringResource(ZoneModel.POLARIZED_THREE.zoneName(i)))
            LegendDot(none, stringResource(R.string.calendar_no_heart_rate))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    load: Double?,
    share: Double?,
    color: Color,
    description: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val number = if (inMonth) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (load != null && share != null) {
            Canvas(Modifier.fillMaxSize()) {
                // Below the day number, so that even the largest dot leaves it readable.
                val largest = size.minDimension * 0.32f
                val radius = (MIN_RADIUS.toPx() + (largest - MIN_RADIUS.toPx()) * share.toFloat())
                drawCircle(color.copy(alpha = if (inMonth) 1f else 0.4f), radius, Offset(size.width / 2, size.height * 0.6f))
            }
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
            color = if (isToday) MaterialTheme.colorScheme.primary else number,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A day with any training shows at least this large a dot. */
private val MIN_RADIUS = 4.dp
