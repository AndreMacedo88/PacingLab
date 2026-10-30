package app.pacinglab.feature.workouts.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.component.icon
import app.pacinglab.core.designsystem.component.label
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.Sport
import app.pacinglab.feature.workouts.R
import app.pacinglab.feature.workouts.originName
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WorkoutsRoute(
    onOpenSession: (String) -> Unit,
    onOpenDataSources: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenSession = onOpenSession,
        onOpenDataSources = onOpenDataSources,
        onOpenSettings = onOpenSettings,
        onSort = viewModel::setSort,
        onFilter = viewModel::setFilter,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    state: WorkoutsUiState,
    onRefresh: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenDataSources: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onOpenSettings: () -> Unit = {},
    onSort: (WorkoutSort) -> Unit = {},
    onFilter: (WorkoutFilter) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workouts_title)) },
                actions = {
                    FilterAction(active = state.filter.active, onClick = { filtering = true })
                    SortAction(state.sort, onSort)
                    SettingsAction(onOpenSettings)
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                val sync = state.sync
                if (sync is SyncStatus.Importing) {
                    item(key = "importing") { ImportingCard(sync, locale, zoneId) }
                }
                val outcome = state.lastOutcome
                // A pause asked for by Health Connect needs nothing from the athlete: the sync carries on by itself,
                // and a pull to refresh already says so in the snackbar.
                when {
                    outcome?.noAccess == true -> item(key = "no-access") {
                        NoticeCard(
                            stringResource(R.string.workouts_no_access),
                            null,
                            stringResource(R.string.workouts_set_up_access),
                            onOpenDataSources,
                        )
                    }

                    outcome != null && outcome.errors.isNotEmpty() && sync == SyncStatus.Idle -> item(key = "failed") {
                        NoticeCard(
                            stringResource(R.string.workouts_sync_failed),
                            outcome.errors.first(),
                            stringResource(R.string.workouts_try_again),
                            onRefresh,
                        )
                    }
                }
                if (!state.loading && state.sessions.isEmpty() && sync == SyncStatus.Idle && outcome?.noAccess != true) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.workouts_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                val shown = state.shown
                if (state.filter.active) {
                    item(key = "filtered") {
                        FilteredRow(shown.size, state.sessions.size, onClear = { onFilter(WorkoutFilter()) })
                    }
                    if (shown.isEmpty()) {
                        item(key = "no-match") {
                            Text(
                                text = stringResource(R.string.workouts_no_match),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                val row: @Composable (SessionItem) -> Unit = { session ->
                    SessionRow(session, state.sort, locale, zoneId, onClick = { onOpenSession(session.id) })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                if (state.sort.byDate) {
                    val byMonth = shown.groupBy { it.start.atZone(it.zoneOffset ?: zoneId).withDayOfMonth(1).toLocalDate() }
                    byMonth.forEach { (month, sessions) ->
                        item(key = "month-$month") {
                            Text(
                                text = monthFormat.format(month).replaceFirstChar { it.titlecase(locale) },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                            )
                        }
                        items(sessions, key = { it.id }) { row(it) }
                    }
                } else {
                    item(key = "sorted") {
                        Text(
                            text = stringResource(state.sort.label()),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(shown, key = { it.id }) { row(it) }
                }
            }
        }
    }
    if (filtering) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { filtering = false }, sheetState = sheet) {
            FilterSheetContent(state.sessions, state.filter, onFilter, onDone = { filtering = false })
        }
    }
}

@Composable
private fun FilterAction(active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (active) Badge() }) {
            Icon(
                painterResource(PacingLabIcons.Filter),
                contentDescription = stringResource(if (active) R.string.workouts_filter_on else R.string.workouts_filter),
            )
        }
    }
}

@Composable
private fun SortAction(sort: WorkoutSort, onSort: (WorkoutSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(PacingLabIcons.Sort), contentDescription = stringResource(R.string.workouts_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WorkoutSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    onClick = {
                        open = false
                        onSort(option)
                    },
                    leadingIcon = {
                        if (option == sort) {
                            Icon(painterResource(PacingLabIcons.Check), contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier.semantics { selected = option == sort },
                )
            }
        }
    }
}

@Composable
private fun FilteredRow(shown: Int, total: Int, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.workouts_shown, total, shown, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear)) }
    }
}

/**
 * The filter sheet's content: a group of chips each for the type of session, how it was recorded and,
 * when sessions come from more than one, the app. Only values that occur are offered, plus any still
 * chosen. Changes apply at once; the button closes the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheetContent(
    sessions: List<SessionItem>,
    filter: WorkoutFilter,
    onFilter: (WorkoutFilter) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val types = remember(sessions) { sessions.map(SessionType::of).toSet() }
    val captures = remember(sessions) { sessions.map { it.capture }.toSet() }
    val origins = remember(sessions) { sessions.map { it.origin }.distinct() }
    val shown = remember(sessions, filter) { sessions.count(filter::matches) }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.workouts_filter), style = MaterialTheme.typography.titleLarge)
        FilterGroup(
            title = stringResource(R.string.filter_type),
            options = SessionType.entries.filter { it in types || it in filter.types },
            chosen = filter.types,
            label = { stringResource(it.label()) },
            onChange = { onFilter(filter.copy(types = it)) },
        )
        FilterGroup(
            title = stringResource(R.string.filter_recorded),
            options = Capture.entries.filter { it in captures || it in filter.captures },
            chosen = filter.captures,
            label = { stringResource(it.filterLabel()) },
            onChange = { onFilter(filter.copy(captures = it)) },
        )
        if (origins.size > 1 || filter.origins.isNotEmpty()) {
            FilterGroup(
                title = stringResource(R.string.filter_app),
                options = (origins + filter.origins).distinct(),
                chosen = filter.origins,
                label = { originName(it) },
                onChange = { onFilter(filter.copy(origins = it)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onFilter(WorkoutFilter()) }, enabled = filter.active) { Text(stringResource(R.string.filter_clear)) }
            Button(onClick = onDone) { Text(pluralStringResource(R.plurals.filter_show, shown, shown)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterGroup(
    title: String,
    options: List<T>,
    chosen: Set<T>,
    label: @Composable (T) -> String,
    onChange: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val selected = option in chosen
                FilterChip(
                    selected = selected,
                    onClick = { onChange(if (selected) chosen - option else chosen + option) },
                    label = { Text(label(option)) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                painterResource(PacingLabIcons.Check),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private fun SessionType.label(): Int = when (this) {
    SessionType.INTERVALS -> R.string.type_intervals
    SessionType.SPRINTS -> R.string.type_sprints
    SessionType.THRESHOLD_TEMPO_RUNS -> R.string.type_threshold_tempo
    SessionType.EASY_RUNS -> R.string.type_easy
    SessionType.LONG_RUNS -> R.string.type_long
    SessionType.OTHER_RUNS -> R.string.type_other_runs
    SessionType.WEIGHTS -> R.string.type_weights
    SessionType.WALKS -> R.string.type_walks
    SessionType.RIDES -> R.string.type_rides
    SessionType.OTHER -> R.string.type_other
}

private fun Capture.filterLabel(): Int = when (this) {
    Capture.RECORDED -> R.string.capture_recorded_filter
    Capture.DETECTED -> R.string.capture_detected_filter
    Capture.ENTERED -> R.string.capture_entered_filter
}

private fun WorkoutSort.label(): Int = when (this) {
    WorkoutSort.NEWEST -> R.string.sort_newest
    WorkoutSort.OLDEST -> R.string.sort_oldest
    WorkoutSort.LONGEST -> R.string.sort_longest
    WorkoutSort.FARTHEST -> R.string.sort_farthest
    WorkoutSort.HIGHEST_LOAD -> R.string.sort_load
    WorkoutSort.HIGHEST_HEART_RATE -> R.string.sort_heart_rate
}

@Composable
private fun ImportingCard(status: SyncStatus.Importing, locale: Locale, zoneId: ZoneId, modifier: Modifier = Modifier) {
    val reached = remember(status.reached, locale) {
        status.reached?.let { DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zoneId).format(it) }
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workouts_importing), style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
            if (reached != null) {
                Text(
                    text = stringResource(R.string.workouts_importing_reached, reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(title: String, detail: String?, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * One session, in three steps of emphasis so that runs stand out: runs started on the watch carry a
 * filled badge, other sessions started on the watch (weights, rides) a lighter tint of it, and sessions
 * an app detected from everyday movement, or that were typed in, an outlined badge and a muted title.
 * The difference never rests on colour alone (the badge's shape differs); screen readers hear where the
 * session came from, and the session page says it under Recording.
 */
@Composable
private fun SessionRow(
    session: SessionItem,
    sort: WorkoutSort,
    locale: Locale,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Out of date order, sessions from different years sit side by side.
    val dateFormat = remember(locale, sort.byDate) { DateTimeFormatter.ofPattern(if (sort.byDate) "EEE d MMM" else "d MMM yyyy", locale) }
    val zone = session.zoneOffset ?: zoneId
    val title = session.title()
    val recorded = session.capture == Capture.RECORDED
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .then(
                    when {
                        !recorded -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)

                        session.sport == Sport.RUN -> Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)

                        else -> Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = SECONDARY_BADGE_ALPHA),
                            CircleShape,
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val sport = stringResource(session.sport.label())
            Icon(
                painter = painterResource(session.sport.icon()),
                contentDescription = when (session.capture) {
                    Capture.RECORDED -> sport
                    Capture.DETECTED -> stringResource(R.string.badge_detected, sport, originName(session.origin))
                    Capture.ENTERED -> stringResource(R.string.badge_entered, sport, originName(session.origin))
                },
                tint = when {
                    !recorded -> MaterialTheme.colorScheme.onSurfaceVariant
                    session.sport == Sport.RUN -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = SECONDARY_ICON_ALPHA)
                },
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (recorded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = dateFormat.format(session.start.atZone(zone)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = metricsLine(session, sort, locale),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.parts > 1) {
                Text(
                    text = pluralStringResource(R.plurals.session_parts, session.parts, session.parts),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun metricsLine(session: SessionItem, sort: WorkoutSort, locale: Locale): String {
    val parts = mutableListOf<String>()
    if (sort == WorkoutSort.HIGHEST_LOAD) {
        session.load?.let { parts += stringResource(R.string.workouts_load, it.roundToInt()) }
    }
    // Step-based distance during strength training is meaningless.
    session.shownDistance?.takeIf { it >= MIN_SHOWN_DISTANCE }?.let {
        parts += stringResource(R.string.unit_km, Formats.kilometres(it, locale))
    }
    parts += Formats.duration(session.movingMillis)
    // Ordered by load, the load takes the place of pace so that the line still fits.
    if ((session.sport == Sport.RUN || session.sport == Sport.WALK) && sort != WorkoutSort.HIGHEST_LOAD) {
        session.distanceMeters?.let { Formats.pacePerKm(session.movingMillis, it) }?.let {
            parts += stringResource(R.string.unit_pace, it)
        }
    }
    session.averageHr?.let { parts += stringResource(R.string.unit_bpm, it) }
    return parts.joinToString("  ·  ")
}

private const val MIN_SHOWN_DISTANCE = 50.0

/** Sessions started on the watch that are not runs: a lighter version of the run badge. */
private const val SECONDARY_BADGE_ALPHA = 0.35f
private const val SECONDARY_ICON_ALPHA = 0.75f
