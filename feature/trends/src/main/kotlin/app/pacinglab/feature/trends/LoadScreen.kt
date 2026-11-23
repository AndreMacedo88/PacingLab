package app.pacinglab.feature.trends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import app.pacinglab.core.analytics.load.LoadBand
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.component.CardActions
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardCard
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.DashboardScaffold
import app.pacinglab.core.designsystem.component.JumpChips
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.component.Span
import app.pacinglab.core.designsystem.component.SpanButtons
import app.pacinglab.core.designsystem.component.dashboardCardColors
import app.pacinglab.core.designsystem.component.heroBorder
import app.pacinglab.core.designsystem.component.rememberFocusedCard
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.ZoneModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LoadRoute(
    title: String,
    tabs: @Composable () -> Unit,
    onOpenPhysiology: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focus: String? = null,
    viewModel: LoadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    LoadScreen(
        state = state,
        title = title,
        tabs = tabs,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onOpenPhysiology = onOpenPhysiology,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
        onWeeksZonesChange = viewModel::setWeeksInFiveZones,
        focus = focus,
    )
}

/**
 * Training > Load: what the training adds up to, from today's training status (the hero) through fitness and
 * form to the weekly table, running against its tolerance and the intensity focus. Chips under the tabs jump
 * to each card.
 */
@Composable
fun LoadScreen(
    state: LoadUiState,
    title: String,
    tabs: @Composable () -> Unit,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
    initialSpan: Span = Span.THREE_MONTHS,
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
    onWeeksZonesChange: (Boolean) -> Unit = {},
    focus: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    val loaded = state as? LoadUiState.Loaded
    val shown = loaded?.takeIf {
        it.days.isNotEmpty()
    }?.cards?.filter { it.visible }?.mapNotNull { LoadCardKind.of(it.id) }?.filter { card ->
        when (card) {
            LoadCardKind.RUNNING -> loaded.running.any { it.km > 0 }
            else -> true
        }
    }.orEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val flashing = rememberFocusedCard(focus, listState, ready = loaded != null) { id ->
        shown.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    }
    val screen = stringResource(R.string.load_screen)
    DashboardScaffold(
        title = title,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        editing = editing,
        onEditingChange = if (loaded != null) ({ editing = it }) else null,
        editTitle = stringResource(R.string.load_edit_title),
        tabs = tabs,
        pinned = if (shown.size > 1) {
            {
                JumpChips(
                    labels = shown.map { stringResource(it.jump) },
                    onJump = { i -> scope.launch { listState.animateScrollToItem(i) } },
                )
            }
        } else {
            null
        },
        editor = { editorModifier ->
            if (loaded != null) {
                DashboardEditor(
                    entries = loaded.cards.mapNotNull { placed ->
                        LoadCardKind.of(placed.id)?.let {
                            DashboardEntry(it.id, stringResource(it.title), stringResource(it.description), placed.visible)
                        }
                    },
                    onChange = { changed -> onArrange(changed.map { PlacedCard(it.id, it.visible) }) },
                    onReset = onResetArrangement,
                    modifier = editorModifier,
                )
            }
        },
        refreshing = refreshing,
        onRefresh = onRefresh,
    ) {
        when (state) {
            LoadUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            LoadUiState.NeedsHeartRateValues -> Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.load_needs_values), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onOpenPhysiology) { Text(stringResource(R.string.load_set_values)) }
            }

            is LoadUiState.Loaded -> if (state.days.isEmpty()) {
                Text(
                    text = stringResource(R.string.load_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(shown, key = { it.id }) { card ->
                        val actions = CardActions(
                            screen = screen,
                            todayId = card.todayId,
                            onHide = { onArrange(state.cards.map { if (it.id == card.id) it.copy(visible = false) else it }) },
                            onArrange = { editing = true },
                        )
                        DashboardCard(Modifier.animateItem(), actions, highlighted = flashing == card.id) {
                            when (card) {
                                LoadCardKind.STATUS -> TrainingStatusCard(state.status, locale, hero = true)

                                LoadCardKind.FORM -> FormCard(state.days, state.today, initialSpan, locale)

                                LoadCardKind.WEEKS -> WeeksCard(
                                    state.weeks,
                                    state.today,
                                    locale,
                                    bands = state.loadBands,
                                    fiveZones = state.fiveZones,
                                    onZonesChange = onWeeksZonesChange,
                                )

                                LoadCardKind.FOCUS -> IntensityFocusCard(state.focus)

                                LoadCardKind.RUNNING -> RunningVolumeCard(state.running, state.today, locale)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormCard(days: List<FormDay>, today: LocalDate, initialSpan: Span, locale: Locale, modifier: Modifier = Modifier) {
    var span by rememberSaveable { mutableStateOf(initialSpan) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val shown = remember(days, span) { days.takeLast(requireNotNull(span.days).toInt()) }
    val index = selected?.takeIf { it in shown.indices } ?: shown.lastIndex
    val day = shown[index]
    val ramp = FitnessModel.rampRate(days, days.size - shown.size + index)
    TrendCard(
        title = stringResource(R.string.form_title),
        info = stringResource(R.string.form_info),
        modifier = modifier,
        advanced = stringResource(R.string.form_advanced),
    ) {
        SpanButtons(FORM_SPANS, span, onSelect = { span = it })
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
 * over the five zones or the three polarized ones ([fiveZones], switched under the table). Load is grey,
 * as the daily load in the fitness chart, so that it is not read as the zone of the same blue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeeksCard(
    weeks: List<WeekSummary>,
    today: LocalDate,
    locale: Locale,
    modifier: Modifier = Modifier,
    bands: Map<LocalDate, LoadBand> = emptyMap(),
    fiveZones: Boolean = false,
    onZonesChange: (Boolean) -> Unit = {},
) {
    val start = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val maxLoad = (weeks.map { it.load } + bands.values.map { it.high }).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val model = if (fiveZones) ZoneModel.LTHR_FIVE else ZoneModel.POLARIZED_THREE
    val zones = zoneColors(if (fiveZones) 5 else 3)
    val bar = chartColors().muted
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    TrendCard(
        title = stringResource(R.string.weeks_title),
        info = stringResource(R.string.weeks_info),
        modifier = modifier,
        advanced = stringResource(R.string.weeks_advanced),
    ) {
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
            val sessions = pluralStringResource(R.plurals.weeks_sessions, week.sessions, week.sessions)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "$label: ${week.load.roundToInt()}, ${Formats.hoursMinutes(week.durationMillis)}, $sessions"
                },
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(WEEK_WIDTH))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    LoadBar(week.load, bands[week.start], maxLoad, bar, Modifier.weight(1f))
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
                IntensityBar(
                    if (fiveZones) week.zoneMillis else week.intensityMillis,
                    zones,
                    Modifier.width(INTENSITY_WIDTH).padding(start = 12.dp),
                )
            }
        }
        if (bands.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 16.dp, height = 10.dp).background(bar.copy(alpha = BAND_ALPHA), RoundedCornerShape(2.dp)))
                Text(stringResource(R.string.weeks_band_legend), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in zones.indices) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(10.dp).background(zones[i], RoundedCornerShape(2.dp)))
                        Text(stringResource(model.zoneName(i)), style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
            }
            ZoneCountToggle(five = fiveZones, onChange = onZonesChange, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * A week's load as a bar over the band that builds fitness (shaded in the bar's colour, full height), the
 * bar thinner than the band so that the band stays visible wherever the load ends.
 */
@Composable
private fun LoadBar(load: Double, band: LoadBand?, scale: Double, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.height(BAR_HEIGHT + 6.dp)) {
        fun x(value: Double) = (value / scale * size.width).toFloat()
        if (band != null) {
            drawRoundRect(
                color.copy(alpha = BAND_ALPHA),
                Offset(x(band.low), 0f),
                Size(x(band.high) - x(band.low), size.height),
                CornerRadius(3.dp.toPx()),
            )
        }
        if (load > 0) {
            val height = BAR_HEIGHT.toPx()
            drawRoundRect(color, Offset(0f, (size.height - height) / 2), Size(x(load), height), CornerRadius(4.dp.toPx()))
        }
    }
}

/** Shares of the zones as one bar split into segments with a small gap between them. */
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
internal fun TrendCard(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    advanced: String? = null,
    hero: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = dashboardCardColors(hero), border = heroBorder(hero)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info, advanced = advanced)
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

/** Form needs a few weeks to show a trend, but a week shows its day-to-day load. */
private val FORM_SPANS = listOf(Span.WEEK, Span.MONTH, Span.THREE_MONTHS, Span.YEAR)

private val WEEK_WIDTH = 72.dp
private val TIME_WIDTH = 56.dp
private val INTENSITY_WIDTH = 84.dp
private val BAR_HEIGHT = 10.dp
private const val BAND_ALPHA = 0.22f
