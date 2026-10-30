package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.load.EffortLoad
import app.pacinglab.core.data.weather.SessionWeatherState
import app.pacinglab.core.designsystem.chart.ZoneLegend
import app.pacinglab.core.designsystem.component.label
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.maps.InteractiveRouteMap
import app.pacinglab.core.maps.RouteMap
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import app.pacinglab.feature.workouts.R
import app.pacinglab.feature.workouts.list.title
import app.pacinglab.feature.workouts.originName
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SessionRoute(sessionId: String, onBack: () -> Unit, onOpenPhysiology: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<SessionViewModel, SessionViewModel.Factory>(key = sessionId) { it.create(sessionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SessionScreen(
        state = state,
        onBack = onBack,
        onOpenPhysiology = onOpenPhysiology,
        modifier = modifier,
        onRateEffort = viewModel::rateEffort,
    )
}

/**
 * A training session. Sessions of several recorded parts get a tab per part; every number, the map
 * and time in zones then describe the selected part only.
 *
 * @param onRateEffort rates the given workouts of a gym session from 1 to 10.
 * @param initialScope tab selected on first display: 0 for the whole session, 1 for the first part.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onBack: () -> Unit,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
    onRateEffort: (List<String>, Int) -> Unit = { _, _ -> },
    zoneId: ZoneId = ZoneId.systemDefault(),
    initialScope: Int = 0,
) {
    val locale = LocalConfiguration.current.locales[0]
    val speedByDefault = (state as? SessionUiState.Loaded)?.speedByDefault ?: false
    var showSpeed by rememberSaveable(speedByDefault) { mutableStateOf(speedByDefault) }
    val toggle = SpeedToggle(
        showSpeed = showSpeed,
        label = stringResource(if (showSpeed) R.string.show_pace else R.string.show_speed),
        onToggle = { showSpeed = !showSpeed },
    )
    // -1 until the athlete picks a tab: the screen opens on the session's focus or on initialScope.
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    val scopes = (state as? SessionUiState.Loaded)?.scopes.orEmpty()
    val opening = maxOf(initialScope, (state as? SessionUiState.Loaded)?.focus ?: 0)
    val index = (if (selected >= 0) selected else opening).coerceIn(0, maxOf(0, scopes.lastIndex))
    val title = (state as? SessionUiState.Loaded)?.session?.title() ?: stringResource(R.string.session_title)
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back))
                        }
                    },
                )
                if (scopes.size > 1) ScopeTabs(scopes, index, onSelect = { selected = it }, locale = locale, zoneId = zoneId)
            }
        },
    ) { padding ->
        when (state) {
            SessionUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            SessionUiState.NotFound -> Text(
                text = stringResource(R.string.session_not_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(24.dp),
            )

            is SessionUiState.Loaded -> ScopeContent(
                scope = scopes[index],
                parts = if (index == 0) scopes.drop(1) else emptyList(),
                effort = state.effortOf(scopes[index]),
                onRateEffort = { onRateEffort(scopes[index].workoutIds, it) },
                toggle = toggle,
                onOpenPhysiology = onOpenPhysiology,
                onSelectPart = { selected = it + 1 },
                locale = locale,
                zoneId = zoneId,
                modifier = Modifier.fillMaxSize().padding(padding),
                weather = state.weather,
                heatAdjustment = state.heatAdjustment,
            )
        }
    }
}

@Composable
private fun ScopeTabs(scopes: List<DetailScope>, selected: Int, onSelect: (Int) -> Unit, locale: Locale, zoneId: ZoneId) {
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    SecondaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
        scopes.forEachIndexed { index, scope ->
            val zone = scope.zoneOffset ?: zoneId
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(scope.name(), maxLines = 1)
                        Text(
                            text = stringResource(
                                R.string.part_time_range,
                                time.format(scope.start.atZone(zone)),
                                time.format(scope.end.atZone(zone)),
                            ),
                            style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun ScopeContent(
    scope: DetailScope,
    parts: List<DetailScope>,
    effort: Int?,
    onRateEffort: (Int) -> Unit,
    toggle: SpeedToggle,
    onOpenPhysiology: () -> Unit,
    onSelectPart: (Int) -> Unit,
    locale: Locale,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
    weather: SessionWeatherState? = null,
    heatAdjustment: Boolean = true,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "when") { When(scope, locale, zoneId) }
        // A rated gym session counts by its rating; session RPE takes the whole session, rests between sets included.
        val rated = effort?.takeIf { scope.sport == Sport.STRENGTH }
        val wholeMillis = scope.end.toEpochMilli() - scope.start.toEpochMilli()
        val stressScore = rated?.let { EffortLoad.stressScore(it, wholeMillis) }
            ?: scope.analysis.load?.takeIf { it.heartRateMillis > 0 }?.stressScore
        item(key = "metrics") { Metrics(scope, stressScore, toggle, locale) }
        // Heart rate says little about lifting, so a gym session leads with its effort.
        if (scope.sport == Sport.STRENGTH && scope.workoutIds.isNotEmpty()) {
            item(key = "effort") { EffortCard(effort, wholeMillis, onRateEffort) }
        }
        if (scope.routes.isNotEmpty()) {
            item(key = "map") { RouteSection(scope) }
        }
        // Nothing to show while the weather is looked up, switched off or without a place.
        if (weather != null && weather != SessionWeatherState.Off && weather != SessionWeatherState.NoPlace) {
            item(key = "conditions") { ConditionsCard(weather, locale, heatAdjustment = heatAdjustment) }
        }
        item(key = "heart-rate") { HeartRateCard(scope.analysis.zones, scope.heartRate, scope.zoneOffset ?: zoneId, onOpenPhysiology) }
        if (scope.analysis.isIntervalSession) {
            item(key = "intervals") { IntervalsCard(scope.analysis, toggle, locale) }
        }
        if (scope.splits.isNotEmpty()) {
            item(key = "splits") { SplitsCard(scope.splits, toggle, locale) }
        }
        scope.form?.let { form ->
            item(key = "form") { RunningFormCard(form, locale) }
        }
        scope.decoupling?.let { decoupling ->
            item(key = "decoupling") { DecouplingCard(decoupling, toggle, locale, heat = weather.heat()) }
        }
        // The effort card shows the load of a rated gym session.
        scope.analysis.load?.takeIf { it.heartRateMillis > 0 && rated == null }?.let { load ->
            item(key = "load") { LoadCard(load) }
        }
        if (parts.isNotEmpty()) {
            item(key = "parts") {
                Text(
                    text = stringResource(R.string.session_parts_heading),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(parts) { index, part -> PartRow(part, onClick = { onSelectPart(index) }, locale, zoneId) }
        }
        scope.recording?.let { recording ->
            item(key = "recording") { RecordingCard(recording, hasRoute = scope.routes.isNotEmpty()) }
        }
    }
}

@Composable
private fun DetailScope.name(): String = when {
    role == PhaseKind.WARM_UP -> stringResource(R.string.scope_warm_up)
    role == PhaseKind.COOL_DOWN -> stringResource(R.string.scope_cool_down)
    recording == null -> stringResource(R.string.scope_whole_session)
    kind != null -> stringResource(kind.label())
    else -> title?.takeIf { it.isNotBlank() } ?: stringResource(sport.label())
}

@Composable
private fun When(scope: DetailScope, locale: Locale, zoneId: ZoneId, modifier: Modifier = Modifier) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale) }
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val zone = scope.zoneOffset ?: zoneId
    Text(
        text = stringResource(
            R.string.session_when,
            date.format(scope.start.atZone(zone)).replaceFirstChar { it.titlecase(locale) },
            time.format(scope.start.atZone(zone)),
            time.format(scope.end.atZone(zone)),
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Metrics(scope: DetailScope, stressScore: Double?, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        scope.distanceMeters?.takeIf { scope.sport != Sport.STRENGTH }?.let {
            Metric(stringResource(R.string.metric_distance), Formats.kilometres(it, locale), "km")
        }
        Metric(stringResource(R.string.metric_duration), Formats.duration(scope.movingMillis), null)
        if (scope.sport == Sport.RUN || scope.sport == Sport.WALK) {
            paceText(scope.movingMillis, scope.distanceMeters, toggle.showSpeed, locale)?.let {
                Metric(
                    label = stringResource(if (toggle.showSpeed) R.string.metric_speed else R.string.metric_pace),
                    value = it,
                    unit = null,
                    modifier = Modifier.speedToggle(toggle),
                )
            }
        }
        scope.averageHr?.let { Metric(stringResource(R.string.metric_avg_hr), it.toString(), "bpm") }
        scope.maxHr?.let { Metric(stringResource(R.string.metric_max_hr), it.toString(), "bpm") }
        stressScore?.let { Metric(stringResource(R.string.metric_load), it.roundToInt().toString(), null) }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.widthIn(min = 96.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (unit == null) value else "$value $unit",
            style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
        )
    }
}

@Composable
private fun RouteSection(scope: DetailScope, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteMap(
            routes = scope.routes,
            contentDescription = stringResource(R.string.map_description),
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.map_open)) { expanded = true },
        )
        if (scope.routeOnlyFrom.isNotEmpty()) {
            Text(
                text = stringResource(R.string.map_only_from, scope.routeOnlyFrom.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (expanded) RouteMapDialog(scope, onDismiss = { expanded = false })
}

/** The route full screen on a map that pans and zooms, coloured by heart rate zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteMapDialog(scope: DetailScope, onDismiss: () -> Unit) {
    val zones = scope.analysis.zones.firstOrNull()?.zoneSet?.takeIf { !scope.heartRate.isEmpty() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.map_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back))
                        }
                    },
                )
                InteractiveRouteMap(scope.routes, scope.heartRate, zones, Modifier.weight(1f).fillMaxWidth())
                if (zones != null) ZoneLegend(zones, Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun PartRow(part: DetailScope, onClick: () -> Unit, locale: Locale, zoneId: ZoneId, modifier: Modifier = Modifier) {
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val zone = part.zoneOffset ?: zoneId
    val details = listOfNotNull(
        stringResource(R.string.part_time_range, time.format(part.start.atZone(zone)), time.format(part.end.atZone(zone))),
        part.distanceMeters?.takeIf {
            part.sport != Sport.STRENGTH
        }?.let { stringResource(R.string.unit_km, Formats.kilometres(it, locale)) },
        Formats.duration(part.movingMillis),
    )
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(part.name(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = details.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(PacingLabIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Where the data came from, for checking a recording that looks wrong. */
@Composable
private fun RecordingCard(recording: RecordingItem, hasRoute: Boolean, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val none = stringResource(R.string.part_none)
    fun count(n: Int) = if (n == 0) none else numbers.format(n)
    val app = originName(recording.origin)
    val how = when (recording.method) {
        RecordingMethod.ACTIVE -> stringResource(R.string.recording_active, app)
        RecordingMethod.AUTOMATIC -> stringResource(R.string.recording_automatic, app)
        RecordingMethod.MANUAL -> stringResource(R.string.recording_manual, app)
        RecordingMethod.UNKNOWN -> stringResource(R.string.recording_unknown, app)
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.recording_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = how + "  ·  " + recording.activity.replace('_', ' '), style = MaterialTheme.typography.bodyMedium)
        if (!hasRoute && recording.isIntervalMode) {
            Text(text = stringResource(R.string.recording_no_gps_intervals), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text =
            stringResource(
                R.string.part_streams,
                count(recording.heartRateSamples),
                count(recording.speedSamples),
                count(recording.routePoints),
            ) +
                "  ·  " + pluralStringResource(R.plurals.part_laps, recording.laps, recording.laps),
            style = MaterialTheme.typography.bodySmall.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
