package app.pacinglab.feature.trends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.ZoneModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrendsRoute(
    onOpenPhysiology: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    TrendsScreen(
        state = state,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onOpenPhysiology = onOpenPhysiology,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    state: TrendsUiState,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
    initialRange: TrendRange = TrendRange.THREE_MONTHS,
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    val loaded = state as? TrendsUiState.Loaded
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing) R.string.trends_edit_title else R.string.trends_title)) },
                actions = {
                    if (loaded != null) DashboardEditAction(editing, onEditingChange = { editing = it })
                    if (!editing) SettingsAction(onOpenSettings)
                },
            )
        },
    ) { scaffoldPadding ->
        if (editing && loaded != null) {
            DashboardEditor(
                entries = loaded.cards.mapNotNull { placed ->
                    TrendsCardKind.of(placed.id)?.let {
                        DashboardEntry(it.id, stringResource(it.title), stringResource(it.description), placed.visible)
                    }
                },
                onChange = { changed -> onArrange(changed.map { PlacedCard(it.id, it.visible) }) },
                onReset = onResetArrangement,
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            )
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when (state) {
                TrendsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                TrendsUiState.NeedsHeartRateValues -> Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.trends_needs_values), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onOpenPhysiology) { Text(stringResource(R.string.trends_set_values)) }
                }

                is TrendsUiState.Loaded -> if (state.days.isEmpty()) {
                    Text(
                        text = stringResource(R.string.trends_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        for (card in state.cards.filter { it.visible }.mapNotNull { TrendsCardKind.of(it.id) }) {
                            when (card) {
                                TrendsCardKind.FORM -> item(key = card.id) { FormCard(state.days, state.today, initialRange, locale) }

                                TrendsCardKind.RECOVERY -> if (state.recovery.isNotEmpty()) {
                                    item(key = card.id) { RecoveryCard(state.recovery, state.today, locale) }
                                }

                                TrendsCardKind.WEEKS -> item(key = card.id) { WeeksCard(state.weeks, state.today, locale) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormCard(days: List<FormDay>, today: LocalDate, initialRange: TrendRange, locale: Locale, modifier: Modifier = Modifier) {
    var range by rememberSaveable { mutableStateOf(initialRange) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val shown = remember(days, range) { days.takeLast(range.days) }
    val index = selected?.takeIf { it in shown.indices } ?: shown.lastIndex
    val day = shown[index]
    val ramp = FitnessModel.rampRate(days, days.size - shown.size + index)
    TrendCard(title = stringResource(R.string.form_title), info = stringResource(R.string.form_info), modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TrendRange.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == range,
                    onClick = { range = option },
                    shape = SegmentedButtonDefaults.itemShape(i, TrendRange.entries.size),
                ) { Text(stringResource(option.label)) }
            }
        }
        Readout(day, ramp, isToday = day.date == today, locale = locale)
        FormChart(
            days = shown,
            selected = selected,
            onSelect = { selected = it },
            description = stringResource(
                R.string.form_chart_description,
                day.fitness.roundToInt(),
                day.fatigue.roundToInt(),
                day.form.roundToInt(),
            ),
            locale = locale,
        )
    }
}

/** The values of one day: today, or the day the finger is on. */
@Composable
private fun Readout(day: FormDay, ramp: Double?, isToday: Boolean, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Value(stringResource(R.string.form_fitness), day.fitness, colors.series[0])
            Value(stringResource(R.string.form_fatigue), day.fatigue, colors.series[1])
            Value(stringResource(R.string.form_form), day.form, null, signed = true)
        }
        val details = listOfNotNull(
            if (isToday) stringResource(R.string.form_today) else date.format(day.date),
            stringResource(R.string.form_day_load, day.load.roundToInt()),
            ramp?.let { stringResource(R.string.form_ramp, signed(it, locale)) },
        )
        Text(
            text = details.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Value(label: String, value: Double, key: Color?, modifier: Modifier = Modifier, signed: Boolean = false) {
    val number = if (signed) signedInt(value) else value.roundToInt().toString()
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = "$label $number" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (key != null) Box(Modifier.size(width = 12.dp, height = 3.dp).background(key, RoundedCornerShape(2.dp)))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(number, style = MaterialTheme.typography.headlineSmall.tabularNumbers())
    }
}

/**
 * The latest weeks, newest first: load as a bar, training time and how the time with heart rate splits
 * over the three polarized zones.
 */
@Composable
private fun WeeksCard(weeks: List<WeekSummary>, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    val start = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val maxLoad = weeks.maxOfOrNull { it.load }?.takeIf { it > 0 } ?: 1.0
    val zones = zoneColors(3)
    val bar = chartColors().series[0]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    TrendCard(title = stringResource(R.string.weeks_title), info = stringResource(R.string.weeks_info), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Header(stringResource(R.string.weeks_week), Modifier.width(WEEK_WIDTH), TextAlign.Start)
            Header(stringResource(R.string.weeks_load), Modifier.weight(1f), TextAlign.Start)
            Header(stringResource(R.string.weeks_time), Modifier.width(TIME_WIDTH))
            Header(stringResource(R.string.weeks_intensity), Modifier.width(INTENSITY_WIDTH).padding(start = 12.dp), TextAlign.Start)
        }
        for (week in weeks) {
            val label = if (!today.isBefore(week.start) && today.isBefore(week.start.plusWeeks(1))) {
                stringResource(R.string.weeks_this_week)
            } else {
                start.format(week.start)
            }
            val sessions = pluralStringResource(R.plurals.weeks_workouts, week.workouts, week.workouts)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "$label: ${week.load.roundToInt()}, ${Formats.hoursMinutes(week.durationMillis)}, $sessions"
                },
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(WEEK_WIDTH))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(BAR_HEIGHT)) {
                        if (week.load > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth((week.load / maxLoad).toFloat())
                                    .height(BAR_HEIGHT)
                                    .background(bar, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                            )
                        }
                    }
                    Text(
                        text = week.load.roundToInt().toString(),
                        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(36.dp),
                    )
                }
                Text(
                    text = Formats.hoursMinutes(week.durationMillis),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(TIME_WIDTH),
                )
                IntensityBar(week.intensityMillis, zones, Modifier.width(INTENSITY_WIDTH).padding(start = 12.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 3) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).background(zones[i], RoundedCornerShape(2.dp)))
                    Text(stringResource(ZoneModel.POLARIZED_THREE.zoneName(i)), style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
        }
    }
}

/** Shares of the three zones as one bar split into segments with a small gap between them. */
@Composable
private fun IntensityBar(millis: LongArray, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = millis.sum()
    Row(modifier = modifier.height(BAR_HEIGHT), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total == 0L) {
            Spacer(Modifier.weight(1f).height(BAR_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)))
        } else {
            millis.forEachIndexed { i, value ->
                if (value > 0) Spacer(Modifier.weight(value.toFloat()).height(BAR_HEIGHT).background(colors[i], RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.End) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
internal fun TrendCard(title: String, info: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info)
            content()
        }
    }
}

private fun signedInt(value: Double): String {
    val rounded = value.roundToInt()
    return when {
        rounded > 0 -> "+$rounded"
        rounded < 0 -> "−${-rounded}"
        else -> "0"
    }
}

private fun signed(value: Double, locale: Locale): String {
    val text = "%.1f".format(locale, kotlin.math.abs(value))
    return when {
        value >= 0.05 -> "+$text"
        value <= -0.05 -> "−$text"
        else -> text
    }
}

private val WEEK_WIDTH = 72.dp
private val TIME_WIDTH = 56.dp
private val INTENSITY_WIDTH = 84.dp
private val BAR_HEIGHT = 10.dp
