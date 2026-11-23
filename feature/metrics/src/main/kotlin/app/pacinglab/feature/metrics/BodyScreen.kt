package app.pacinglab.feature.metrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.CardActions
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardCard
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.DashboardScaffold
import app.pacinglab.core.designsystem.component.dashboardCardColors
import app.pacinglab.core.designsystem.component.heroBorder
import app.pacinglab.core.designsystem.component.rememberFocusedCard
import app.pacinglab.core.ui.EmptyTile
import app.pacinglab.core.ui.HeartRateTodayCard
import app.pacinglab.core.ui.NightlyTile
import app.pacinglab.core.ui.NightlyTileData
import app.pacinglab.core.ui.TileRow
import app.pacinglab.core.ui.VitalsVerdict
import app.pacinglab.core.ui.label
import java.time.LocalDate
import java.time.ZoneId
import app.pacinglab.core.ui.R as UiR

@Composable
fun BodyRoute(
    onOpenMetric: (NightlyMetric) -> Unit,
    onOpenHeartRate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focus: String? = null,
    viewModel: BodyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    BodyScreen(
        state = state,
        onOpenMetric = onOpenMetric,
        onOpenHeartRate = onOpenHeartRate,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
        focus = focus,
    )
}

/**
 * The body's response to training and life, measured overnight and through the day: last night's vitals over a
 * tile per nightly measure, each with its week and range (the hero), HRV and resting heart rate over months,
 * today's heart rate and daytime strain. Every tile opens its measure's page.
 */
@Composable
fun BodyScreen(
    state: BodyUiState,
    onOpenMetric: (NightlyMetric) -> Unit,
    onOpenHeartRate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
    focus: String? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    val loaded = state as? BodyUiState.Loaded
    val shown = loaded?.cards?.filter { it.visible }?.mapNotNull { BodyCardKind.of(it.id) }?.filter { card ->
        when (card) {
            BodyCardKind.RECOVERY -> loaded.recovery.isNotEmpty()
            else -> true
        }
    }.orEmpty()
    val listState = rememberLazyListState()
    val flashing = rememberFocusedCard(focus, listState, ready = loaded != null) { id ->
        shown.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    }
    val screen = stringResource(R.string.body_title)
    DashboardScaffold(
        title = screen,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        editing = editing,
        onEditingChange = if (loaded != null) ({ editing = it }) else null,
        editTitle = stringResource(R.string.body_edit_title),
        editor = { editorModifier ->
            if (loaded != null) {
                DashboardEditor(
                    entries = loaded.cards.mapNotNull { placed ->
                        BodyCardKind.of(placed.id)?.let {
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
            BodyUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is BodyUiState.Loaded -> LazyColumn(
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
                            BodyCardKind.VITALS -> VitalsHero(
                                vitals = state.vitals,
                                tiles = state.tiles,
                                today = state.today,
                                onOpenMetric = onOpenMetric,
                                tileActions = { metric ->
                                    CardActions(screen = screen, todayId = BODY_TILES[metric], onArrange = { editing = true })
                                },
                            )

                            BodyCardKind.RECOVERY -> RecoveryCard(state.recovery, state.today, locale, variation = state.variation)

                            BodyCardKind.HEART_RATE -> HeartRateTodayCard(
                                series = state.heartRate,
                                zones = state.zones,
                                restingHr = state.restingHr,
                                locale = locale,
                                zoneId = zoneId,
                                onClick = onOpenHeartRate,
                            )

                            BodyCardKind.STRAIN -> StrainCard(state.strain, locale)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Last night's vitals in a word over a tile per nightly measure, two to a row, each with its week as a sparkline
 * over the athlete's range and a status pill. A long press on a tile pins that tile to Today.
 */
@Composable
private fun VitalsHero(
    vitals: Vitals?,
    tiles: List<NightlyTileData>,
    today: LocalDate,
    onOpenMetric: (NightlyMetric) -> Unit,
    tileActions: (NightlyMetric) -> CardActions,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val labels = weekdayInitials((NightlyTileData.DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }, locale)
    Card(modifier = modifier.fillMaxWidth(), colors = dashboardCardColors(hero = true), border = heroBorder()) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                CardHeader(
                    title = stringResource(UiR.string.vitals_title),
                    info = stringResource(UiR.string.vitals_info),
                    advanced = stringResource(UiR.string.ui_nightly_range_advanced),
                )
                if (vitals != null && vitals.judged > 0) {
                    VitalsVerdict(vitals, name = { stringResource(it.label()) })
                } else {
                    Text(
                        stringResource(R.string.body_no_night),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (row in tiles.chunked(2)) {
                TileRow {
                    for (tile in row) {
                        DashboardCard(Modifier.weight(1f), tileActions(tile.metric)) {
                            NightlyTile(tile, labels, onClick = { onOpenMetric(tile.metric) }, locale = locale)
                        }
                    }
                    if (row.size == 1) EmptyTile()
                }
            }
        }
    }
}

@Composable
internal fun BodyCard(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    advanced: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = dashboardCardColors()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info, advanced = advanced)
            content()
        }
    }
}
