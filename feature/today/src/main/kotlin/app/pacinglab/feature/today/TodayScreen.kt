package app.pacinglab.feature.today

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.designsystem.chart.HeartRateChart
import app.pacinglab.core.designsystem.chart.MiniBars
import app.pacinglab.core.designsystem.chart.SegmentBar
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.CardActions
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardCard
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.DashboardScaffold
import app.pacinglab.core.designsystem.component.SectionHeading
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.component.SharedBounds
import app.pacinglab.core.designsystem.component.SharedKeys
import app.pacinglab.core.designsystem.component.dashboardCardColors
import app.pacinglab.core.designsystem.component.icon
import app.pacinglab.core.designsystem.component.label
import app.pacinglab.core.designsystem.component.rememberFocusedCard
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.maps.RouteLegend
import app.pacinglab.core.maps.RouteMap
import app.pacinglab.core.maps.RouteShade
import app.pacinglab.core.maps.rememberRouteShades
import app.pacinglab.core.model.RouteColoring
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.Sport
import app.pacinglab.core.ui.EmptyTile
import app.pacinglab.core.ui.HeartRateTodayCard
import app.pacinglab.core.ui.NightlyTile
import app.pacinglab.core.ui.TileRow
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Where Today's cards lead. */
sealed interface TodayDestination {
    data class Session(val id: String) : TodayDestination

    data class Metric(val metric: NightlyMetric) : TodayDestination

    data object Readiness : TodayDestination

    data object HeartRate : TodayDestination

    data object Physiology : TodayDestination

    data object Alertness : TodayDestination

    data object ReactionTest : TodayDestination

    /** A card's home on another tab, which opens scrolled to the card with the id [focus]. */
    data class Tab(val tab: TodayTab, val focus: String) : TodayDestination
}

/** The tabs Today's summaries lead to. */
enum class TodayTab { LOAD, PERFORMANCE, BODY, SLEEP_NIGHT, SLEEP_PATTERNS }

@Composable
fun TodayRoute(
    onOpen: (TodayDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focus: String? = null,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var locationRefused by rememberSaveable { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshWeather() else locationRefused = true
    }
    // Location may have been allowed in the system settings meanwhile.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshWeather()
        onPauseOrDispose {}
    }
    TodayScreen(
        state = state,
        onOpen = onOpen,
        modifier = modifier,
        onOpenSettings = onOpenSettings,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onRate = viewModel::rate,
        onRateGym = viewModel::rateGym,
        onArrange = viewModel::arrange,
        onResetArrangement = viewModel::resetArrangement,
        focus = focus,
        weatherAccess = WeatherAccess(
            locationRefused = locationRefused,
            onAllowLocation = {
                if (locationRefused) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                } else {
                    askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            onRetry = viewModel::refreshWeather,
        ),
    )
}

/** A row of Today: a heading over a group, or one wide card, or one or two tiles. */
private sealed interface TodayRow {
    val key: String

    data class Heading(val section: TodaySection) : TodayRow {
        override val key: String get() = "heading-${section.name}"
    }

    data class Cards(val cards: List<TodayCard>) : TodayRow {
        override val key: String get() = cards.joinToString("+") { it.id }
    }
}

/**
 * The day as a briefing, arranged by the athlete: what waits to be done, readiness as the answer to "how am I",
 * alertness, last night's vitals and today's heart rate, then training: the latest session, the training
 * status and the load tiles, and the weather. Every card opens its detail or its home on another tab, scrolled
 * to it. The pencil in the app bar, or a long press on a card, switches to arranging the cards.
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onOpen: (TodayDestination) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRate: (Int) -> Unit = {},
    onRateGym: (List<String>, Int) -> Unit = { _, _ -> },
    onArrange: (List<PlacedCard>) -> Unit = {},
    onResetArrangement: () -> Unit = {},
    initiallyEditing: Boolean = false,
    weatherAccess: WeatherAccess = WeatherAccess(),
    focus: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    // The morning question leaves Today once answered, after a moment in which the answer shows.
    var answers by remember { mutableIntStateOf(0) }
    LaunchedEffect(answers) {
        if (answers > 0) {
            delay(ANSWER_SHOWN_MILLIS)
            answers = 0
        }
    }
    val rate: (Int) -> Unit = {
        answers++
        onRate(it)
    }
    val loaded = state as? TodayUiState.Loaded
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val listState = rememberLazyListState()
    val rows = loaded?.rows(answering = answers > 0).orEmpty()
    val flashing = rememberFocusedCard(focus, listState, ready = loaded != null) { id ->
        rows.indexOfFirst { row -> row is TodayRow.Cards && row.cards.any { it.id == id } }.takeIf { it >= 0 }
    }
    val screen = stringResource(R.string.today_title)
    val actions: (TodayCard) -> CardActions = { card ->
        CardActions(
            screen = screen,
            onHide = loaded?.let {
                {
                    onArrange(
                        it.cards.map { placed ->
                            if (placed.id ==
                                card.id
                            ) {
                                placed.copy(visible = false)
                            } else {
                                placed
                            }
                        },
                    )
                }
            },
            onArrange = { editing = true },
        )
    }
    DashboardScaffold(
        title = screen,
        subtitle = loaded?.let { dateFormat.format(it.today).replaceFirstChar { c -> c.titlecase(locale) } },
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        editing = editing,
        onEditingChange = if (loaded != null) ({ editing = it }) else null,
        editTitle = stringResource(R.string.today_edit_title),
        editor = { editorModifier ->
            if (loaded != null) {
                DashboardEditor(
                    entries = loaded.cards.mapNotNull { placed ->
                        TodayCard.of(placed.id)?.let { card ->
                            DashboardEntry(card.id, stringResource(card.title), stringResource(card.description), placed.visible)
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
            TodayUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is TodayUiState.Loaded -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is TodayRow.Heading -> row.section.title?.let { SectionHeading(stringResource(it), Modifier.animateItem()) }

                        is TodayRow.Cards -> if (row.cards.size == 1 && row.cards.single().wide) {
                            val card = row.cards.single()
                            DashboardCard(Modifier.animateItem(), actions(card), highlighted = flashing == card.id) {
                                WideCard(
                                    card = card,
                                    state = state,
                                    onOpen = onOpen,
                                    onRate = rate,
                                    onRateGym = onRateGym,
                                    weatherAccess = weatherAccess,
                                    locale = locale,
                                    zoneId = zoneId,
                                    answering = answers > 0,
                                )
                            }
                        } else {
                            TileRow(Modifier.animateItem()) {
                                row.cards.forEach { card ->
                                    DashboardCard(Modifier.weight(1f), actions(card), highlighted = flashing == card.id) {
                                        TileCard(card, state, onOpen)
                                    }
                                }
                                if (row.cards.size == 1) EmptyTile()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What the To do card holds now; the morning question stays while [answering] so that the answer shows. */
private fun TodayUiState.Loaded.todo(answering: Boolean) = TodoItems(
    rested = rested == null || answering,
    rating = rested,
    gym = gymToRate,
    testDue = extras.alertness?.testDue == true,
    valuesMissing = !heartRateValuesSet,
)

/**
 * The visible cards that have something to show, in the athlete's order, as rows: a wide card alone, tiles two
 * at a time, and a heading where a new group starts.
 */
private fun TodayUiState.Loaded.rows(answering: Boolean = false): List<TodayRow> {
    val shown = cards.filter { it.visible }.mapNotNull { TodayCard.of(it.id) }.filter { card ->
        when (card) {
            TodayCard.TODO -> todo(answering).any

            TodayCard.LAST_WORKOUT -> lastWorkout != null

            TodayCard.VO2MAX -> extras.vo2max?.trend != null

            TodayCard.RACE_5K -> extras.running?.predictions?.any { it.fromVo2Max != null || it.fromCriticalSpeed != null } == true

            TodayCard.EFFICIENCY -> !extras.efficiency?.trend.isNullOrEmpty()

            // Only the Google Health API supplies these.
            TodayCard.OXYGEN_SATURATION -> nights.any { it.oxygenSaturation != null }

            TodayCard.SKIN_TEMPERATURE -> nights.any { it.skinTemperature != null }

            TodayCard.SLEEP_HEALTH -> sleepHealth.isNotEmpty()

            TodayCard.SLEEP_DEBT -> sleepDebts.isNotEmpty()

            else -> true
        }
    }
    val rows = mutableListOf<TodayRow>()
    var section = TodaySection.MORNING
    var waiting: TodayCard? = null
    fun flush() {
        waiting?.let { rows += TodayRow.Cards(listOf(it)) }
        waiting = null
    }
    for (card in shown) {
        if (card.section != section) {
            flush()
            section = card.section
            if (section.title != null) rows += TodayRow.Heading(section)
        }
        when {
            card.wide -> {
                flush()
                rows += TodayRow.Cards(listOf(card))
            }

            waiting == null -> waiting = card

            else -> {
                rows += TodayRow.Cards(listOf(waiting!!, card))
                waiting = null
            }
        }
    }
    flush()
    // A group heading used twice (the athlete moved a card) keeps only its first place.
    return rows.distinctBy { it.key }
}

@Composable
private fun WideCard(
    card: TodayCard,
    state: TodayUiState.Loaded,
    onOpen: (TodayDestination) -> Unit,
    onRate: (Int) -> Unit,
    onRateGym: (List<String>, Int) -> Unit,
    weatherAccess: WeatherAccess,
    locale: Locale,
    zoneId: ZoneId,
    answering: Boolean,
) {
    when (card) {
        TodayCard.TODO -> TodoCard(
            items = state.todo(answering),
            today = state.today,
            zoneId = zoneId,
            onRate = onRate,
            onRateGym = { effort -> state.gymToRate?.let { onRateGym(it.workoutIds, effort) } },
            onTest = { onOpen(TodayDestination.ReactionTest) },
            onOpenPhysiology = { onOpen(TodayDestination.Physiology) },
        )

        TodayCard.READINESS -> SharedBounds(SharedKeys.READINESS) {
            ReadinessCard(
                state.readiness,
                state.readinessHistory,
                state.days(locale),
                onClick = { onOpen(TodayDestination.Readiness) },
                hero = true,
            )
        }

        TodayCard.ALERTNESS -> AlertnessTodayCard(
            state.extras.alertness,
            state.today,
            zoneId,
            onClick = { onOpen(TodayDestination.Alertness) },
            onTest = { onOpen(TodayDestination.ReactionTest) },
        )

        TodayCard.VITALS -> VitalsTodayCard(state.vitals(), onClick = { onOpen(TodayDestination.Tab(TodayTab.BODY, BODY_VITALS)) })

        TodayCard.TRAINING_STATUS -> TrainingStatusTodayCard(
            state.extras.trainingStatus,
            onClick = { onOpen(TodayDestination.Tab(TodayTab.LOAD, LOAD_STATUS)) },
        )

        TodayCard.HEART_RATE -> HeartRateTodayCard(
            series = state.extras.heartRate?.series,
            zones = state.extras.heartRateZones,
            restingHr = state.lastNight()?.restingHr,
            locale = locale,
            zoneId = zoneId,
            onClick = { onOpen(TodayDestination.HeartRate) },
        )

        TodayCard.LAST_WORKOUT -> state.lastWorkout?.let { workout ->
            LastWorkoutCard(
                workout,
                state.today,
                locale,
                zoneId,
                onClick = { onOpen(TodayDestination.Session(workout.sessionId)) },
                routeColoring = state.routeColoring,
            )
        }

        TodayCard.WEATHER -> WeatherCard(state.extras.weather, locale, weatherAccess)

        else -> Unit
    }
}

@Composable
private fun TileCard(card: TodayCard, state: TodayUiState.Loaded, onOpen: (TodayDestination) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val tab: (TodayTab, String) -> () -> Unit = { tab, focus -> { onOpen(TodayDestination.Tab(tab, focus)) } }
    val nightly: @Composable (NightlyMetric) -> Unit = { metric ->
        NightlyTile(
            data = state.nightlyTile(metric),
            labels = state.days(locale),
            onClick = { onOpen(TodayDestination.Metric(metric)) },
            modifier = modifier,
            locale = locale,
        )
    }
    when (card) {
        TodayCard.SLEEP_SCORE -> SleepTile(state, onClick = tab(TodayTab.SLEEP_NIGHT, SLEEP_TILES), modifier)
        TodayCard.SLEEP_HEALTH -> SleepHealthTile(state, onClick = tab(TodayTab.SLEEP_PATTERNS, SLEEP_HABITS), modifier)
        TodayCard.SLEEP_DEBT -> SleepDebtTile(state, onClick = tab(TodayTab.SLEEP_PATTERNS, SLEEP_HABITS), modifier)
        TodayCard.HRV -> nightly(NightlyMetric.HRV)
        TodayCard.RESTING_HR -> nightly(NightlyMetric.RESTING_HR)
        TodayCard.BREATHING_RATE -> nightly(NightlyMetric.BREATHING_RATE)
        TodayCard.SLEEPING_HR -> nightly(NightlyMetric.SLEEPING_HR)
        TodayCard.OXYGEN_SATURATION -> nightly(NightlyMetric.OXYGEN_SATURATION)
        TodayCard.SKIN_TEMPERATURE -> nightly(NightlyMetric.SKIN_TEMPERATURE)
        TodayCard.STRAIN -> StrainTile(state, onClick = tab(TodayTab.BODY, BODY_STRAIN), modifier)
        TodayCard.FORM -> FormTile(state, onClick = tab(TodayTab.LOAD, LOAD_FORM), modifier)
        TodayCard.WEEK_LOAD -> WeekTile(state, onClick = tab(TodayTab.LOAD, LOAD_WEEKS), modifier)
        TodayCard.VO2MAX -> Vo2MaxTile(state, onClick = tab(TodayTab.PERFORMANCE, PERFORMANCE_VO2MAX), modifier)
        TodayCard.RACE_5K -> RaceTile(state, onClick = tab(TodayTab.PERFORMANCE, PERFORMANCE_RACES), modifier)
        TodayCard.EFFICIENCY -> EfficiencyTile(state, onClick = tab(TodayTab.PERFORMANCE, PERFORMANCE_EFFICIENCY), modifier)
        else -> Unit
    }
}

/** The ids of the cards on other tabs that Today's summaries open; each tab's catalogue uses the same. */
private const val BODY_VITALS = "vitals"
private const val BODY_STRAIN = "strain"
private const val LOAD_STATUS = "status"
private const val LOAD_FORM = "form"
private const val LOAD_WEEKS = "weeks"
private const val SLEEP_TILES = "tiles"
private const val SLEEP_HABITS = "habits"
private const val PERFORMANCE_VO2MAX = "vo2max"
private const val PERFORMANCE_RACES = "races"
private const val PERFORMANCE_EFFICIENCY = "efficiency"

@Composable
private fun LastWorkoutCard(
    workout: LastWorkout,
    today: LocalDate,
    locale: Locale,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    routeColoring: RouteColoring = RouteColoring.ZONES_FIVE,
) {
    val zone = workout.zoneOffset ?: zoneId
    val start = workout.start.atZone(zone)
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val day = when (start.toLocalDate()) {
        today -> stringResource(R.string.day_today)
        today.minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.format(start).replaceFirstChar { it.titlecase(locale) }
    }
    val facts = listOfNotNull(
        workout.distanceMeters?.takeIf {
            workout.sport != Sport.STRENGTH
        }?.let { stringResource(R.string.unit_km, Formats.kilometres(it, locale)) },
        Formats.duration(workout.movingMillis),
        workout.distanceMeters?.takeIf { workout.sport == Sport.RUN || workout.sport == Sport.WALK }
            ?.let { Formats.pacePerKm(workout.movingMillis, it) }
            ?.let { stringResource(R.string.unit_pace, it) },
        workout.load?.let { stringResource(R.string.workout_load, it.roundToInt()) },
    )
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = dashboardCardColors()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.workout_title))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(workout.sport.icon()),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = workout.kind?.let { stringResource(it.label()) }
                            ?: workout.titles.joinToString(" + ").ifEmpty { stringResource(workout.sport.label()) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$day, ${time.format(start)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                facts.forEach { Text(it, style = MaterialTheme.typography.bodyLarge.tabularNumbers()) }
            }
            val zones = workout.zones
            if (zones != null && workout.heartRate.size > 1) {
                HeartRateChart(
                    heartRate = workout.heartRate,
                    zones = zones,
                    zoneId = zone,
                    description = stringResource(R.string.workout_heart_rate),
                    height = 100.dp,
                    scrubbable = false,
                )
            }
            if (workout.routes.isNotEmpty()) {
                val zoneSets = listOfNotNull(workout.zones, workout.polarizedZones)
                val shade = rememberRouteShades(workout.routes, workout.heartRate, zoneSets, workout.sport).of(routeColoring)
                RouteMap(routes = workout.routes, contentDescription = stringResource(R.string.workout_map), height = 120.dp, shade = shade)
                // The heart rate chart above already names the five zones.
                val chartLegend = zones != null && workout.heartRate.size > 1
                if (!(chartLegend && shade is RouteShade.Zones && shade.zones == zones)) RouteLegend(shade)
            }
        }
    }
}

/** How long the morning question stays on Today after an answer, so that the answer is seen. */
private const val ANSWER_SHOWN_MILLIS = 1_500L
