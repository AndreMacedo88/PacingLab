package app.pacinglab.feature.performance

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.component.CardActions
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardCard
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.DashboardScaffold
import app.pacinglab.core.designsystem.component.JumpChips
import app.pacinglab.core.designsystem.component.SectionHeading
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.component.Span
import app.pacinglab.core.designsystem.component.dashboardCardColors
import app.pacinglab.core.designsystem.component.heroBorder
import app.pacinglab.core.designsystem.component.rememberFocusedCard
import app.pacinglab.core.designsystem.format.Formats
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun PerformanceRoute(
    onOpenWorkout: (String) -> Unit,
    onOpenPhysiology: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focus: String? = null,
    viewModel: PerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    PerformanceScreen(
        state = state,
        onOpenWorkout = onOpenWorkout,
        modifier = modifier,
        onSaveTest = viewModel::saveTest,
        onDeleteTest = viewModel::deleteTest,
        onOpenPhysiology = onOpenPhysiology,
        onOpenSettings = onOpenSettings,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
        focus = focus,
    )
}

/** A row of Performance: a section's heading, or a card. */
private sealed interface PerformanceRow {
    val key: String

    data class Heading(val section: PerformanceSection) : PerformanceRow {
        override val key: String get() = "heading-${section.name}"
    }

    data class Item(val card: PerformanceCardKind) : PerformanceRow {
        override val key: String get() = card.id
    }
}

/**
 * What the athlete can do, in four sections with chips that jump to each: capacity (VO2max, the hero; critical
 * speed; the heart rate values and zones), racing (predictions and training paces), records (best efforts) and
 * economy (aerobic efficiency, decoupling, heart rate recovery). Training load lives on Training > Load.
 */
@Composable
fun PerformanceScreen(
    state: PerformanceUiState,
    onOpenWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSaveTest: (LocalDate, Double) -> Unit = { _, _ -> },
    onDeleteTest: (LocalDate) -> Unit = {},
    onOpenPhysiology: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    initialEffortSpan: Span = Span.THREE_MONTHS,
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
    focus: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    val loaded = state as? PerformanceUiState.Loaded
    val rows = loaded?.rows().orEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val flashing = rememberFocusedCard(focus, listState, ready = loaded != null) { id ->
        rows.indexOfFirst { it is PerformanceRow.Item && it.card.id == id }.takeIf { it >= 0 }
    }
    val screen = stringResource(R.string.performance_title)
    val headings = rows.withIndex().filter { it.value is PerformanceRow.Heading }
    DashboardScaffold(
        title = screen,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        editing = editing,
        onEditingChange = if (loaded != null) ({ editing = it }) else null,
        editTitle = stringResource(R.string.performance_edit_title),
        pinned = if (headings.size > 1) {
            {
                JumpChips(
                    labels = headings.map { stringResource((it.value as PerformanceRow.Heading).section.title) },
                    onJump = { i -> scope.launch { listState.animateScrollToItem(headings[i].index) } },
                )
            }
        } else {
            null
        },
        editor = { editorModifier ->
            if (loaded != null) {
                DashboardEditor(
                    entries = loaded.cards.mapNotNull { placed ->
                        PerformanceCardKind.of(placed.id)?.let {
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
            PerformanceUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is PerformanceUiState.Loaded -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val running = state.running
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is PerformanceRow.Heading -> SectionHeading(stringResource(row.section.title), Modifier.animateItem())

                        is PerformanceRow.Item -> {
                            val card = row.card
                            val actions = CardActions(
                                screen = screen,
                                todayId = card.todayId,
                                onHide = { onArrange(state.cards.map { if (it.id == card.id) it.copy(visible = false) else it }) },
                                onArrange = { editing = true },
                            )
                            DashboardCard(Modifier.animateItem(), actions, highlighted = flashing == card.id) {
                                when (card) {
                                    PerformanceCardKind.VO2MAX -> Vo2MaxCard(
                                        state.vo2max,
                                        state.today,
                                        onSaveTest,
                                        onDeleteTest,
                                        locale,
                                        fitness = state.fitness,
                                        hero = true,
                                    )

                                    PerformanceCardKind.RACES -> RaceCard(
                                        running.predictions,
                                        running.vo2max,
                                        locale,
                                        shape = running.shape,
                                    )

                                    PerformanceCardKind.PACES -> running.paces?.let { TrainingPacesCard(it, locale) }

                                    PerformanceCardKind.CRITICAL_SPEED -> CriticalSpeedCard(running.criticalSpeed, onOpenWorkout, locale)

                                    PerformanceCardKind.ZONES -> ZonesCard(state.heartRateValues, onOpen = onOpenPhysiology)

                                    PerformanceCardKind.EFFORTS -> BestEffortsCard(
                                        running.recent,
                                        running.allTime,
                                        initialEffortSpan,
                                        onOpenWorkout,
                                        locale,
                                    )

                                    PerformanceCardKind.EFFICIENCY -> EfficiencyCard(state.efficiency, state.today, locale)

                                    PerformanceCardKind.RECOVERY -> RepRecoveryCard(state.recoveries, state.today, locale)

                                    PerformanceCardKind.DECOUPLING -> DecouplingCard(running.decoupling, onOpenWorkout, locale)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The visible cards with something to show, in the athlete's order, with a heading where a section starts. */
private fun PerformanceUiState.Loaded.rows(): List<PerformanceRow> {
    val shown = cards.filter { it.visible }.mapNotNull { PerformanceCardKind.of(it.id) }.filter { card ->
        when (card) {
            PerformanceCardKind.RACES -> running.predictions.any { it.fromVo2Max != null || it.fromCriticalSpeed != null }
            PerformanceCardKind.PACES -> running.paces != null
            PerformanceCardKind.EFFORTS -> !running.allTime.isEmpty()
            PerformanceCardKind.EFFICIENCY -> efficiency.trend.isNotEmpty()
            PerformanceCardKind.RECOVERY -> recoveries.isNotEmpty()
            PerformanceCardKind.DECOUPLING -> running.decoupling.isNotEmpty()
            else -> true
        }
    }
    val rows = mutableListOf<PerformanceRow>()
    var section: PerformanceSection? = null
    for (card in shown) {
        if (card.section != section) {
            section = card.section
            rows += PerformanceRow.Heading(card.section)
        }
        rows += PerformanceRow.Item(card)
    }
    // A section heading used twice (the athlete moved a card out of its group) keeps only its first place.
    return rows.distinctBy { it.key }
}

@Composable
internal fun PerformanceCard(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    advanced: String? = null,
    hero: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info, advanced = advanced)
            content()
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxWidth(), colors = dashboardCardColors(hero), border = heroBorder(hero)) { body() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = dashboardCardColors(hero), border = heroBorder(hero)) {
            body()
        }
    }
}

@Composable
internal fun RaceDistance.label(): String = stringResource(
    when (this) {
        RaceDistance.M400 -> R.string.distance_400m
        RaceDistance.K1 -> R.string.distance_1k
        RaceDistance.K3 -> R.string.distance_3k
        RaceDistance.K5 -> R.string.distance_5k
        RaceDistance.K10 -> R.string.distance_10k
        RaceDistance.HALF_MARATHON -> R.string.distance_half
        RaceDistance.MARATHON -> R.string.distance_marathon
    },
)

/** Finishing time, "18:53" or "1:26:38". */
internal fun raceTime(seconds: Double): String = Formats.duration((seconds * 1000).roundToLong())

/** Pace "3:47" per kilometre at [speed] m/s. */
internal fun paceText(speed: Double): String {
    val seconds = (1000.0 / speed).roundToInt()
    return "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
}

/** Pace "3:47" for [meters] in [seconds]. */
internal fun pace(seconds: Double, meters: Double): String = Formats.pacePerKm((seconds * 1000).roundToLong(), meters) ?: "–"
