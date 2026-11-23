package app.pacinglab.feature.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.SleepFactors
import app.pacinglab.core.analytics.recovery.SleepRating
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
import app.pacinglab.core.designsystem.component.SubTabs
import app.pacinglab.core.designsystem.component.dashboardCardColors
import app.pacinglab.core.designsystem.component.rememberFocusedCard
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SleepRoute(
    initialDate: String?,
    onOpenSettings: () -> Unit,
    onOpenMetric: (NightlyMetric) -> Unit,
    onOpenAlertness: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    initialView: SleepView = SleepView.NIGHT,
    focus: String? = null,
    viewModel: SleepViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val factors by viewModel.factors.collectAsStateWithLifecycle()
    val alertness by viewModel.alertness.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    SleepScreen(
        state = state,
        initialDate = initialDate?.let(LocalDate::parse),
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onOpenMetric = onOpenMetric,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
        onRate = viewModel::rate,
        factors = factors,
        alertness = alertness,
        onOpenAlertness = onOpenAlertness,
        onTest = onTest,
        initialView = initialView,
        focus = focus,
    )
}

/**
 * Sleep in two views. Night: one night in depth, laid out like the sleep screen of Google Health, with the
 * night's score and time asleep (the hero), the alertness the athlete can expect through the following day, the
 * morning question, the night's stages, the score's parts, sleep quality against published ranges, heart rate
 * through the night and the night's vitals; arrows under the tabs step from night to night and stay in place.
 * Patterns: what many nights show, up to the latest: sleep health and debt (the hero), key metrics, timing, how
 * the score follows the morning answers, and what goes with better and worse nights, with chips that jump to
 * each. A top-level screen: settings sit at the right of the app bar, beside the pencil that arranges the cards
 * of the view shown.
 */
@Composable
fun SleepScreen(
    state: SleepUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate? = null,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onOpenMetric: (NightlyMetric) -> Unit = {},
    onArrange: (SleepView, List<PlacedCard>) -> Unit = { _, _ -> },
    onResetArrangement: (SleepView) -> Unit = {},
    initiallyEditing: Boolean = false,
    onRate: (LocalDate, Int) -> Unit = { _, _ -> },
    factors: SleepFactors? = null,
    alertness: AlertnessHistory? = null,
    onOpenAlertness: () -> Unit = {},
    onTest: () -> Unit = {},
    zoneId: ZoneId = ZoneId.systemDefault(),
    initialView: SleepView = SleepView.NIGHT,
    focus: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    var view by rememberSaveable { mutableStateOf(initialView) }
    val loaded = state as? SleepUiState.Loaded
    val nights = loaded?.nights.orEmpty()
    var selectedDay by rememberSaveable { mutableLongStateOf(initialDate?.toEpochDay() ?: Long.MAX_VALUE) }
    val index = nights.indexOfLast { it.date.toEpochDay() <= selectedDay }.takeIf { it >= 0 } ?: nights.lastIndex
    // Night follows the night picked; Patterns always looks back from the latest one.
    val night = if (view == SleepView.NIGHT) nights.getOrNull(index) else nights.lastOrNull()
    val shown = if (loaded != null && night != null) loaded.shownCards(view, night) else emptyList()
    val nightList = rememberLazyListState()
    val patternList = rememberLazyListState()
    val listState = if (view == SleepView.NIGHT) nightList else patternList
    val scope = rememberCoroutineScope()
    val flashing = rememberFocusedCard(focus, listState, ready = shown.isNotEmpty()) { id ->
        shown.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    }
    val title = stringResource(R.string.sleep_title)
    val screen = stringResource(view.title)
    DashboardScaffold(
        title = title,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        editing = editing,
        onEditingChange = if (loaded != null) ({ editing = it }) else null,
        editTitle = stringResource(view.editTitle),
        tabs = {
            SubTabs(
                titles = SleepView.entries.map { stringResource(it.title) },
                selected = view.ordinal,
                onSelect = { view = SleepView.entries[it] },
            )
        },
        pinned = when {
            night == null -> null

            view == SleepView.NIGHT -> (
                {
                    NightSelector(
                        night = night,
                        locale = locale,
                        onPrevious = nights.getOrNull(index - 1)?.let { { selectedDay = it.date.toEpochDay() } },
                        onNext = nights.getOrNull(index + 1)?.let { { selectedDay = it.date.toEpochDay() } },
                    )
                }
                )

            else -> {
                val jumps = shown.withIndex().filter { it.value.jump != null }
                (
                    {
                        JumpChips(
                            labels = jumps.map { stringResource(it.value.jump!!) },
                            onJump = { i -> scope.launch { listState.animateScrollToItem(jumps[i].index) } },
                        )
                    }
                    )
            }
        },
        editor = { editorModifier ->
            if (loaded != null) {
                DashboardEditor(
                    entries = loaded.cards(view).mapNotNull { placed ->
                        SleepCardKind.of(placed.id, view)?.let {
                            DashboardEntry(it.id, stringResource(it.title), stringResource(it.description), placed.visible)
                        }
                    },
                    onChange = { changed -> onArrange(view, changed.map { PlacedCard(it.id, it.visible) }) },
                    onReset = { onResetArrangement(view) },
                    modifier = editorModifier,
                )
            }
        },
        refreshing = refreshing,
        onRefresh = onRefresh,
    ) {
        when {
            state is SleepUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            loaded == null || night == null -> Text(
                text = stringResource(R.string.sleep_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )

            else -> {
                val score = loaded.scores.getValue(night.date)
                val need = loaded.needs.getValue(night.date)
                val arrange = { editing = true }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { "${view.name}-${it.id}" }) { card ->
                        val actions = CardActions(
                            screen = screen,
                            onHide = { onArrange(view, loaded.cards(view).map { if (it.id == card.id) it.copy(visible = false) else it }) },
                            onArrange = arrange,
                        )
                        val tileActions: (
                            String?,
                        ) -> CardActions = { todayId -> CardActions(screen = screen, todayId = todayId, onArrange = arrange) }
                        DashboardCard(Modifier.animateItem(), actions, highlighted = flashing == card.id) {
                            when (card) {
                                SleepCardKind.ALERTNESS -> AlertnessCard(
                                    alertness,
                                    night.date,
                                    zoneId,
                                    onOpen = onOpenAlertness,
                                    onTest = onTest,
                                )

                                SleepCardKind.TILES -> NightTiles(loaded, night, locale, tileActions = tileActions)

                                SleepCardKind.RESTED -> RestedQuestionCard(
                                    morning = night.date,
                                    rating = loaded.rested[night.date],
                                    onRate = { onRate(night.date, it) },
                                    today = loaded.today,
                                )

                                SleepCardKind.NIGHT -> NightCard(night, score, locale, rested = loaded.rested[night.date])

                                SleepCardKind.SCORE -> ScoreCard(night, score, need)

                                SleepCardKind.QUALITY -> QualityCard(night)

                                SleepCardKind.HEART_RATE -> HeartRateCard(night, locale, onClick = {
                                    onOpenMetric(NightlyMetric.SLEEPING_HR)
                                })

                                SleepCardKind.RECOVERY -> VitalsCard(nights, night, onOpenMetric)

                                SleepCardKind.HABITS -> HabitTiles(loaded, night.date, locale, tileActions = tileActions)

                                SleepCardKind.HEALTH -> loaded.health[night.date]?.let { HealthCard(it, need) }

                                SleepCardKind.KEY_METRICS -> KeyMetricsCard(nights, night.date, loaded.scores, loaded.needs, locale)

                                SleepCardKind.TIMING -> SleepTimingCard(nights, night, loaded.freeDays)

                                SleepCardKind.FACTORS -> SleepFactorsCard(factors, morning = night.date, rested = loaded.rested[night.date])

                                SleepCardKind.FEELING -> FeelingCard(agreement = loaded.feeling, morning = night.date)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The cards of [view] the athlete shows that have something to say about [night]. */
private fun SleepUiState.Loaded.shownCards(view: SleepView, night: Night): List<SleepCardKind> =
    cards(view).filter { it.visible }.mapNotNull { SleepCardKind.of(it.id, view) }.filter { card ->
        when (card) {
            SleepCardKind.HEALTH -> health[night.date] != null
            SleepCardKind.QUALITY -> night.continuity != null
            SleepCardKind.HEART_RATE -> night.sleepingHeartRate != null
            else -> true
        }
    }

@Composable
private fun NightSelector(night: Night, locale: Locale, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
            Icon(painterResource(PacingLabIcons.ChevronLeft), stringResource(R.string.sleep_previous))
        }
        Text(
            text = date.format(night.date).replaceFirstChar { it.titlecase(locale) },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(painterResource(PacingLabIcons.ChevronRight), stringResource(R.string.sleep_next))
        }
    }
}

@Composable
internal fun SleepCard(
    title: String,
    info: String,
    modifier: Modifier = Modifier,
    advanced: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = dashboardCardColors()
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
        Card(modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors) { body() }
    }
}

internal fun SleepRating.label(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_good
    SleepRating.FAIR -> R.string.sleep_fair
    SleepRating.POOR -> R.string.sleep_poor
}

internal fun clock(minuteOfDay: Int): String = "%02d:%02d".format(Locale.ROOT, minuteOfDay / 60, minuteOfDay % 60)

internal const val MINUTE = 60_000.0
internal const val HOUR = 3_600_000.0
internal const val DASH = "–"
internal const val WEEK = 7L
internal const val MONTH = 30L
