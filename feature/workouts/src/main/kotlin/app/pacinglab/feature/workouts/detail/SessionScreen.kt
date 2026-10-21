package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.Sport
import app.pacinglab.feature.workouts.R
import app.pacinglab.feature.workouts.list.SessionItem
import app.pacinglab.feature.workouts.list.label
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SessionRoute(sessionId: String, onBack: () -> Unit, onOpenPhysiology: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = hiltViewModel<SessionViewModel, SessionViewModel.Factory>(key = sessionId) { it.create(sessionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SessionScreen(state = state, onBack = onBack, onOpenPhysiology = onOpenPhysiology, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    state: SessionUiState,
    onBack: () -> Unit,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    val toggle = SpeedToggle(
        showSpeed = showSpeed,
        label = stringResource(if (showSpeed) R.string.show_pace else R.string.show_speed),
        onToggle = { showSpeed = !showSpeed },
    )
    val title = (state as? SessionUiState.Loaded)?.session?.let { session ->
        session.titles.joinToString(" + ").ifEmpty { stringResource(session.sport.label()) }
    } ?: stringResource(R.string.session_title)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back))
                    }
                },
            )
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

            is SessionUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Header(state.session, locale, zoneId) }
                item { Metrics(state.session, toggle, locale) }
                if (state.analysis.isIntervalSession) {
                    item { IntervalsCard(state.analysis, toggle, locale) }
                }
                item { ZonesCard(state.analysis.zones, onOpenPhysiology) }
                item {
                    Text(
                        text = stringResource(R.string.session_parts_heading),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(state.parts, key = { it.id }) { part -> PartCard(part, locale, zoneId) }
            }
        }
    }
}

@Composable
private fun Header(session: SessionItem, locale: Locale, zoneId: ZoneId, modifier: Modifier = Modifier) {
    val format = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", locale) }
    Text(
        text = format.format(session.start.atZone(session.zoneOffset ?: zoneId)).replaceFirstChar { it.titlecase(locale) },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Metrics(session: SessionItem, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        session.distanceMeters?.takeIf { session.sport != Sport.STRENGTH }?.let {
            Metric(stringResource(R.string.metric_distance), Formats.kilometres(it, locale), "km")
        }
        Metric(stringResource(R.string.metric_duration), Formats.duration(session.movingMillis), null)
        if (session.sport == Sport.RUN || session.sport == Sport.WALK) {
            paceText(session.movingMillis, session.distanceMeters, toggle.showSpeed, locale)?.let {
                Metric(
                    label = stringResource(if (toggle.showSpeed) R.string.metric_speed else R.string.metric_pace),
                    value = it,
                    unit = null,
                    modifier = Modifier.speedToggle(toggle),
                )
            }
        }
        session.averageHr?.let { Metric(stringResource(R.string.metric_avg_hr), it.toString(), "bpm") }
        session.maxHr?.let { Metric(stringResource(R.string.metric_max_hr), it.toString(), "bpm") }
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
private fun PartCard(part: PartItem, locale: Locale, zoneId: ZoneId, modifier: Modifier = Modifier) {
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val zone = part.zoneOffset ?: zoneId
    val none = stringResource(R.string.part_none)
    fun count(n: Int) = if (n == 0) none else numbers.format(n)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(part.title ?: stringResource(part.sport.label()), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.part_time_range,
                    time.format(part.start.atZone(zone)),
                    time.format(part.end.atZone(zone)),
                ) + "  ·  " + part.activity.replace('_', ' '),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text =
                stringResource(R.string.part_streams, count(part.heartRateSamples), count(part.speedSamples), count(part.routePoints)) +
                    "  ·  " + pluralStringResource(R.plurals.part_laps, part.laps, part.laps),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
