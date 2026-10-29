package app.pacinglab.feature.settings.physiology

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.zones.ThresholdHeartRateEstimate
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.ZoneSwatch
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.component.zoneNumber
import app.pacinglab.core.designsystem.component.zoneRange
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.ZoneBoundaries
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import app.pacinglab.feature.settings.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun PhysiologyRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PhysiologyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PhysiologyScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        modifier = modifier,
        onSetZones = viewModel::setZones,
    )
}

/** Which entry the editor shows: none, a new one, or the entry starting on a given day. */
private const val EDITOR_CLOSED = Long.MIN_VALUE
private const val EDITOR_NEW = Long.MAX_VALUE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysiologyScreen(
    state: PhysiologyUiState,
    onBack: () -> Unit,
    onSave: (PhysiologyEntry, LocalDate?) -> Unit,
    onDelete: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    onSetZones: (ZoneModel, List<Int>?) -> Unit = { _, _ -> },
) {
    var editor by rememberSaveable { mutableLongStateOf(EDITOR_CLOSED) }
    val loaded = state as? PhysiologyUiState.Loaded
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.physiology_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (loaded?.current != null) {
                ExtendedFloatingActionButton(
                    onClick = { editor = EDITOR_NEW },
                    icon = { Icon(painterResource(PacingLabIcons.Add), contentDescription = null) },
                    text = { Text(stringResource(R.string.physiology_add)) },
                )
            }
        },
    ) { padding ->
        when (state) {
            PhysiologyUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is PhysiologyUiState.Loaded -> PhysiologyContent(
                state = state,
                onAdd = { editor = EDITOR_NEW },
                onEdit = { editor = it.effectiveFrom.toEpochDay() },
                onSetZones = onSetZones,
                contentPadding = padding,
            )
        }
    }

    if (loaded != null && editor != EDITOR_CLOSED) {
        val editing = loaded.entries.firstOrNull { it.effectiveFrom.toEpochDay() == editor }
        PhysiologyEditor(
            editing = editing,
            template = editing ?: loaded.current?.entry,
            today = loaded.today,
            measuredRestingHr = loaded.measuredRestingHr,
            estimatedMaxHr = loaded.estimatedMax?.bpm,
            estimatedThresholdHr = loaded.estimatedThreshold?.bpm,
            takenDates = loaded.entries.map { it.effectiveFrom }.toSet() - setOfNotNull(editing?.effectiveFrom),
            onDismiss = { editor = EDITOR_CLOSED },
            onSave = { entry ->
                onSave(entry, editing?.effectiveFrom)
                editor = EDITOR_CLOSED
            },
            onDelete = editing?.let { entry ->
                {
                    onDelete(entry.effectiveFrom)
                    editor = EDITOR_CLOSED
                }
            },
        )
    }
}

@Composable
private fun PhysiologyContent(
    state: PhysiologyUiState.Loaded,
    onAdd: () -> Unit,
    onEdit: (PhysiologyEntry) -> Unit,
    onSetZones: (ZoneModel, List<Int>?) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val current = state.current
        if (current == null) {
            item { EmptyCard(onAdd) }
            return@LazyColumn
        }
        item { CurrentCard(current, estimatedThreshold = state.estimatedThreshold) }
        item { ZonesCard(current, state.boundaries, onSetZones) }
        if (state.entries.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.physiology_history)) }
            items(state.entries, key = { it.effectiveFrom.toEpochDay() }) { entry ->
                HistoryItem(entry, locale, onClick = { onEdit(entry) })
            }
        }
    }
}

@Composable
private fun EmptyCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.physiology_empty), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAdd) { Text(stringResource(R.string.physiology_set_values)) }
        }
    }
}

@Composable
private fun CurrentCard(current: CurrentPhysiology, estimatedThreshold: ThresholdHeartRateEstimate?, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(stringResource(R.string.physiology_current), info = stringResource(R.string.physiology_current_info))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Value(
                    label = stringResource(R.string.physiology_max_hr),
                    value = stringResource(R.string.physiology_bpm, current.anchors.maxHr),
                    note = stringResource(if (current.maxEstimated) R.string.physiology_max_estimated else R.string.physiology_set_by_you),
                    modifier = Modifier.weight(1f),
                )
                Value(
                    label = stringResource(R.string.physiology_threshold_hr),
                    value = stringResource(R.string.physiology_bpm, current.anchors.thresholdHr),
                    note = when {
                        !current.thresholdEstimated -> null

                        estimatedThreshold?.bpm == current.anchors.thresholdHr ->
                            stringResource(R.string.physiology_threshold_from_run, dateFormat.format(estimatedThreshold.measuredOn))

                        else -> stringResource(R.string.physiology_estimated)
                    },
                    modifier = Modifier.weight(1f),
                )
                Value(
                    label = stringResource(R.string.physiology_resting_hr),
                    value = current.anchors.restingHr?.let { stringResource(R.string.physiology_bpm, it) } ?: DASH,
                    note = stringResource(
                        when {
                            current.restingSetByAthlete -> R.string.physiology_set_by_you
                            current.anchors.restingHr != null -> R.string.physiology_resting_measured
                            else -> R.string.physiology_resting_not_measured
                        },
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Value(label: String, value: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers())
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZonesCard(
    current: CurrentPhysiology,
    boundaries: ZoneBoundaries,
    onSetZones: (ZoneModel, List<Int>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val zones = if (selected == 0) current.fiveZones else current.threeZones
    val custom = if (selected == 0) boundaries.five != null else boundaries.polarized != null
    if (editing) {
        ZoneBoundariesDialog(
            zones = zones,
            custom = custom,
            onSave = {
                onSetZones(zones.model, it)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(stringResource(R.string.physiology_zones))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(R.string.physiology_zones_five, R.string.physiology_zones_three).forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selected == index,
                        onClick = { selected = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
            }
            ZoneTable(zones)
            Text(
                text = stringResource(
                    when {
                        custom -> R.string.zones_custom_caption
                        selected == 0 -> R.string.physiology_zones_five_caption
                        else -> R.string.physiology_zones_three_caption
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { editing = true }) {
                Text(stringResource(if (custom) R.string.zones_edit_own else R.string.zones_set_own))
            }
        }
    }
}

@Composable
private fun ZoneTable(zones: ZoneSet, modifier: Modifier = Modifier) {
    val colors = zoneColors(zones.size)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hardest zone first, as zones are usually pictured stacked from the top.
        for (index in zones.zones.indices.reversed()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZoneSwatch(colors[index])
                Text(
                    text = zoneNumber(index),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp).width(32.dp),
                )
                Text(
                    stringResource(zones.model.zoneName(index)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(zoneRange(zones, index), style = MaterialTheme.typography.bodyMedium.tabularNumbers())
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: PhysiologyEntry, locale: Locale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val format = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    val parts = listOfNotNull(
        entry.maxHr?.let { stringResource(R.string.physiology_entry_max, it) },
        entry.thresholdHr?.let { stringResource(R.string.physiology_entry_threshold, it) },
        entry.restingHr?.let { stringResource(R.string.physiology_entry_resting, it) },
    ).ifEmpty { listOf(stringResource(R.string.physiology_entry_automatic)) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.physiology_from, format.format(entry.effectiveFrom))) },
        supportingContent = { Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.tabularNumbers()) },
        trailingContent = { Icon(painterResource(PacingLabIcons.Edit), contentDescription = stringResource(R.string.physiology_edit)) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

private const val DASH = "–"

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}
