package app.pacinglab.feature.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.designsystem.chart.HistoryChart
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.RangeStanding
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Every night with the metric's values; the page derives each period from it. */
data class NightlyMetricState(val metric: NightlyMetric, val nights: List<Night>, val today: LocalDate)

@HiltViewModel(assistedFactory = NightlyMetricViewModel.Factory::class)
class NightlyMetricViewModel @AssistedInject constructor(@Assisted metric: NightlyMetric, nights: NightRepository, clock: Clock) :
    ViewModel() {
    val state: StateFlow<NightlyMetricState?> = nights.observeSleep()
        .map { NightlyMetricState(metric, it.nights, LocalDate.now(clock)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @AssistedFactory
    interface Factory {
        fun create(metric: NightlyMetric): NightlyMetricViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun NightlyMetricRoute(metric: NightlyMetric, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<NightlyMetricViewModel, NightlyMetricViewModel.Factory>(key = metric.name) { it.create(metric) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NightlyMetricScreen(metric, state, onBack, modifier)
}

/**
 * One nightly measure in depth: the chosen period's nights against the usual range of each night,
 * the selected night's value and standing (touch the chart to pick another), the period's average,
 * lowest, highest and nights in range, a list of the nights, and what the measure is and how it is
 * worked out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightlyMetricScreen(
    metric: NightlyMetric,
    state: NightlyMetricState?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSpan: Span = Span.WEEK,
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(metric.title())) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        var span by rememberSaveable { mutableIntStateOf(initialSpan.ordinal) }
        var end by rememberSaveable { mutableLongStateOf(state.today.toEpochDay()) }
        val period = Period(Span.entries[span], LocalDate.ofEpochDay(end))
        var selected by rememberSaveable(span, end) { mutableStateOf<Int?>(null) }
        val byDate = remember(state.nights) { state.nights.associateBy { it.date } }
        // At the resolution shown, so that a dot is coloured as its pill says.
        val values = period.days.map { day -> byDate[day]?.let(metric.value)?.let(metric::rounded) }
        val ranges = remember(state.nights, period) { period.days.map { NightlyRange.of(state.nights, metric, it) } }
        val trend = remember(state.nights, period) {
            if (period.span.days > Span.MONTH.days) rollingAverage(period.days, byDate, metric) else emptyList()
        }
        val shown = selected ?: values.indexOfLast { it != null }.takeIf { it >= 0 }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "period") {
                PeriodControls(period, state.today, onPeriod = {
                    span = it.span.ordinal
                    end = it.end.toEpochDay()
                }, locale = locale)
            }
            item(key = "readout") {
                Readout(
                    metric,
                    shown?.let {
                        period.days[it]
                    },
                    shown?.let { values[it] },
                    shown?.let { ranges[it] },
                    byDate,
                    state.today,
                    locale,
                )
            }
            item(key = "chart") {
                HistoryChart(
                    values = values,
                    color = metric.color(),
                    description = stringResource(R.string.metric_chart_description, stringResource(metric.title()), period.label(locale)),
                    bands = ranges.map { it?.let { r -> r.low..r.high } },
                    trend = trend,
                    labels = period.axisLabels(locale),
                    selected = shown,
                    onSelect = { selected = it },
                    format = { metric.number(it, locale) },
                )
            }
            item(key = "stats") { Stats(metric, values, ranges, locale) }
            if (period.span == Span.WEEK || period.span == Span.MONTH) {
                item(key = "nights") { NightList(metric, period, values, ranges, byDate, locale) }
            }
            item(key = "about") {
                About(stringResource(R.string.metric_about_title, stringResource(metric.title())), stringResource(metric.about()))
            }
        }
    }
}

@Composable
private fun Readout(
    metric: NightlyMetric,
    date: LocalDate?,
    value: Double?,
    range: NightlyRange?,
    byDate: Map<LocalDate, Night>,
    today: LocalDate,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(value?.let { metric.formatted(it, locale) } ?: DASH, style = MaterialTheme.typography.displaySmall.tabularNumbers())
            when {
                value != null -> RangePill(range?.standing(value))

                metric == NightlyMetric.HRV && date != null && byDate[date]?.hrvPartial == true ->
                    StatusPill(stringResource(R.string.metric_partial), MaterialTheme.colorScheme.outline, PacingLabIcons.Info)
            }
        }
        if (date != null) {
            Text(
                nightLabel(date, today, locale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        range?.let {
            Text(
                stringResource(R.string.metric_range, metric.number(it.low, locale), metric.formatted(it.high, locale), it.nights),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stats(
    metric: NightlyMetric,
    values: List<Double?>,
    ranges: List<NightlyRange?>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val known = values.filterNotNull()
    if (known.isEmpty()) return
    val judged = values.indices.filter { values[it] != null && ranges[it] != null }
    val inside = judged.count { ranges[it]!!.standing(values[it]!!) == RangeStanding.IN_RANGE }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Stat(stringResource(R.string.metric_average), metric.formatted(known.average(), locale), Modifier.weight(1f))
        Stat(stringResource(R.string.metric_lowest), metric.formatted(known.min(), locale), Modifier.weight(1f))
        Stat(stringResource(R.string.metric_highest), metric.formatted(known.max(), locale), Modifier.weight(1f))
        if (judged.isNotEmpty()) Stat(stringResource(R.string.metric_in_range), "$inside/${judged.size}", Modifier.weight(1f))
    }
}

@Composable
internal fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium.tabularNumbers(), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NightList(
    metric: NightlyMetric,
    period: Period,
    values: List<Double?>,
    ranges: List<NightlyRange?>,
    byDate: Map<LocalDate, Night>,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    Column(modifier) {
        for (i in period.days.indices.reversed()) {
            val day = period.days[i]
            val value = values[i]
            val partial = metric == NightlyMetric.HRV && byDate[day]?.hrvPartial == true
            if (value == null && !partial) continue
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (value != null) {
                    RangePill(ranges[i]?.standing(value), Modifier.padding(end = 12.dp))
                    Text(metric.formatted(value, locale), style = MaterialTheme.typography.titleMedium.tabularNumbers())
                } else {
                    Text(
                        stringResource(R.string.metric_partial),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun About(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The average of each day and the six before it, so that a long period reads as a trend. */
private fun rollingAverage(days: List<LocalDate>, byDate: Map<LocalDate, Night>, metric: NightlyMetric): List<Double?> = days.map { day ->
    (0L..6L).mapNotNull { back -> byDate[day.minusDays(back)]?.let(metric.value) }.takeIf { it.size >= MIN_TREND_NIGHTS }?.average()
}

@Composable
internal fun nightLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when (date) {
    today -> stringResource(R.string.last_night)
    else -> stringResource(R.string.night_of, DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(date))
}

internal fun NightlyMetric.title(): Int = when (this) {
    NightlyMetric.HRV -> R.string.metric_hrv
    NightlyMetric.RESTING_HR -> R.string.metric_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.metric_breathing
    NightlyMetric.SLEEPING_HR -> R.string.metric_sleeping_hr
    NightlyMetric.OXYGEN_SATURATION -> R.string.metric_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.metric_skin_temperature
}

private fun NightlyMetric.about(): Int = when (this) {
    NightlyMetric.HRV -> R.string.about_hrv
    NightlyMetric.RESTING_HR -> R.string.about_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.about_breathing
    NightlyMetric.SLEEPING_HR -> R.string.about_sleeping_hr
    NightlyMetric.OXYGEN_SATURATION -> R.string.about_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.about_skin_temperature
}

@Composable
private fun NightlyMetric.color() = when (this) {
    NightlyMetric.HRV -> chartColors().series[0]
    NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> chartColors().restingHr
    NightlyMetric.BREATHING_RATE -> chartColors().series[2]
    NightlyMetric.OXYGEN_SATURATION -> chartColors().oxygenSaturation
    NightlyMetric.SKIN_TEMPERATURE -> chartColors().skinTemperature
}

/** The number alone: "62", "13.4", "+0.3". */
internal fun NightlyMetric.number(value: Double, locale: Locale): String = when (this) {
    NightlyMetric.BREATHING_RATE, NightlyMetric.OXYGEN_SATURATION -> "%.1f".format(locale, value)
    NightlyMetric.SKIN_TEMPERATURE -> "%+.1f".format(locale, value)
    else -> value.roundToInt().toString()
}

@Composable
internal fun NightlyMetric.formatted(value: Double, locale: Locale): String = stringResource(
    when (this) {
        NightlyMetric.HRV -> R.string.metric_unit_ms
        NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> R.string.metric_unit_bpm
        NightlyMetric.BREATHING_RATE -> R.string.unit_breaths
        NightlyMetric.OXYGEN_SATURATION -> R.string.unit_percent
        NightlyMetric.SKIN_TEMPERATURE -> R.string.unit_celsius
    },
    number(value, locale),
)

internal const val DASH = "–"
private const val MIN_TREND_NIGHTS = 4
