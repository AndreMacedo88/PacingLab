package app.pacinglab.feature.workouts.detail

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.maps.RouteMap
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import app.pacinglab.feature.workouts.R
import app.pacinglab.feature.workouts.list.label
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SessionRoute(sessionId: String, onBack: () -> Unit, onOpenPhysiology: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<SessionViewModel, SessionViewModel.Factory>(key = sessionId) { it.create(sessionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SessionScreen(state = state, onBack = onBack, onOpenPhysiology = onOpenPhysiology, modifier = modifier)
}

/**
 * A training session. Sessions of several recorded parts get a tab per part; every number, the map
 * and time in zones then describe the selected part only.
 *
 * @param initialScope tab selected on first display: 0 for the whole session, 1 for the first part.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onBack: () -> Unit,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    initialScope: Int = 0,
) {
    val locale = LocalConfiguration.current.locales[0]
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    val toggle = SpeedToggle(
        showSpeed = showSpeed,
        label = stringResource(if (showSpeed) R.string.show_pace else R.string.show_speed),
        onToggle = { showSpeed = !showSpeed },
    )
    var selected by rememberSaveable { mutableIntStateOf(initialScope) }
    val scopes = (state as? SessionUiState.Loaded)?.scopes.orEmpty()
    val index = selected.coerceIn(0, maxOf(0, scopes.lastIndex))
    val title = (state as? SessionUiState.Loaded)?.session?.let { session ->
        session.titles.joinToString(" + ").ifEmpty { stringResource(session.sport.label()) }
    } ?: stringResource(R.string.session_title)
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
                toggle = toggle,
                onOpenPhysiology = onOpenPhysiology,
                onSelectPart = { selected = it + 1 },
                locale = locale,
                zoneId = zoneId,
                modifier = Modifier.fillMaxSize().padding(padding),
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
    toggle: SpeedToggle,
    onOpenPhysiology: () -> Unit,
    onSelectPart: (Int) -> Unit,
    locale: Locale,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "when") { When(scope, locale, zoneId) }
        item(key = "metrics") { Metrics(scope, toggle, locale) }
        if (scope.routes.isNotEmpty()) {
            item(key = "map") { RouteSection(scope) }
        }
        if (scope.analysis.isIntervalSession) {
            item(key = "intervals") { IntervalsCard(scope.analysis, toggle, locale) }
        }
        item(key = "zones") { ZonesCard(scope.analysis.zones, onOpenPhysiology) }
        scope.analysis.load?.takeIf { it.heartRateMillis > 0 }?.let { load ->
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
private fun DetailScope.name(): String = title?.takeIf { it.isNotBlank() }
    ?: if (recording == null) stringResource(R.string.scope_whole_session) else stringResource(sport.label())

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
private fun Metrics(scope: DetailScope, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
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
        scope.analysis.load?.takeIf { it.heartRateMillis > 0 }?.let {
            Metric(stringResource(R.string.metric_load), it.stressScore.roundToInt().toString(), null)
        }
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteMap(routes = scope.routes, contentDescription = stringResource(R.string.map_description))
        if (scope.routeOnlyFrom.isNotEmpty()) {
            Text(
                text = stringResource(R.string.map_only_from, scope.routeOnlyFrom.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val app = when (recording.origin) {
        FITBIT -> stringResource(R.string.origin_fitbit)
        GOOGLE_FIT -> stringResource(R.string.origin_google_fit)
        else -> recording.origin
    }
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

private const val FITBIT = "com.fitbit.FitbitMobile"
private const val GOOGLE_FIT = "com.google.android.apps.fitness"
