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
import androidx.compose.runtime.getValue
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
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.DashboardEditAction
import app.pacinglab.core.designsystem.component.DashboardEditor
import app.pacinglab.core.designsystem.component.DashboardEntry
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.component.icon
import app.pacinglab.core.designsystem.component.label
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.maps.RouteMap
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.Sport
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

    data object Sleep : TodayDestination

    data object Trends : TodayDestination

    data object Performance : TodayDestination

    data object Physiology : TodayDestination
}

@Composable
fun TodayRoute(
    onOpen: (TodayDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
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

/**
 * The day at a glance, arranged by the athlete: readiness and the morning question, today's heart
 * rate, tiles for last night and recent training, each with a mini chart of the last days and a status
 * pill, and the latest workout. Every card opens its detail. The pencil in the app bar switches to
 * arranging the cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    BackHandler(enabled = editing) { editing = false }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (editing) {
                        Text(stringResource(R.string.today_edit_title))
                    } else {
                        Column {
                            Text(stringResource(R.string.today_title))
                            if (state is TodayUiState.Loaded) {
                                val format = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
                                Text(
                                    text = format.format(state.today).replaceFirstChar { it.titlecase(locale) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (state is TodayUiState.Loaded) DashboardEditAction(editing, onEditingChange = { editing = it })
                    if (!editing) SettingsAction(onOpenSettings)
                },
            )
        },
    ) { scaffoldPadding ->
        if (editing && state is TodayUiState.Loaded) {
            val entries = state.cards.mapNotNull { placed ->
                TodayCard.of(placed.id)?.let { card ->
                    DashboardEntry(card.id, stringResource(card.title), stringResource(card.description), placed.visible)
                }
            }
            DashboardEditor(
                entries = entries,
                onChange = { changed -> onArrange(changed.map { PlacedCard(it.id, it.visible) }) },
                onReset = onResetArrangement,
                modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            )
            return@Scaffold
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when (state) {
                TodayUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is TodayUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!state.heartRateValuesSet) {
                        item(key = "values") { SetValuesCard(onOpenPhysiology = { onOpen(TodayDestination.Physiology) }) }
                    }
                    for (row in state.rows()) {
                        item(key = row.joinToString("+") { it.id }) {
                            if (row.size == 1 && row.single().wide) {
                                WideCard(row.single(), state, onOpen, onRate, onRateGym, weatherAccess, locale, zoneId)
                            } else {
                                TileRow {
                                    row.forEach { TileCard(it, state, onOpen, Modifier.weight(1f)) }
                                    if (row.size == 1) EmptyTile()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The visible cards that have something to show, in the athlete's order, as rows: a wide card alone,
 * tiles two at a time.
 */
private fun TodayUiState.Loaded.rows(): List<List<TodayCard>> {
    val shown = cards.filter { it.visible }.mapNotNull { TodayCard.of(it.id) }.filter { card ->
        when (card) {
            TodayCard.GYM_EFFORT -> gymToRate != null

            TodayCard.LAST_WORKOUT -> lastWorkout != null

            TodayCard.VO2MAX -> extras.vo2max?.trend != null

            TodayCard.RACE_5K -> extras.running?.predictions?.any { it.fromVo2Max != null || it.fromCriticalSpeed != null } == true

            TodayCard.EFFICIENCY -> !extras.efficiency?.trend.isNullOrEmpty()

            // Only the Google Health API supplies these.
            TodayCard.OXYGEN_SATURATION -> nights.any { it.oxygenSaturation != null }

            TodayCard.SKIN_TEMPERATURE -> nights.any { it.skinTemperature != null }

            else -> true
        }
    }
    val rows = mutableListOf<List<TodayCard>>()
    var waiting: TodayCard? = null
    for (card in shown) {
        when {
            card.wide -> {
                waiting?.let { rows += listOf(it) }
                waiting = null
                rows += listOf(card)
            }

            waiting == null -> waiting = card

            else -> {
                rows += listOf(waiting, card)
                waiting = null
            }
        }
    }
    waiting?.let { rows += listOf(it) }
    return rows
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
) {
    when (card) {
        TodayCard.READINESS -> ReadinessCard(
            state.readiness,
            state.readinessHistory,
            state.days(locale),
            onClick = { onOpen(TodayDestination.Readiness) },
        )

        TodayCard.RESTED -> RestedCard(state.rested, onRate)

        TodayCard.GYM_EFFORT -> state.gymToRate?.let { gym ->
            GymEffortCard(gym, state.today, zoneId, onRate = { onRateGym(gym.workoutIds, it) })
        }

        TodayCard.HEART_RATE -> HeartRateTodayCard(
            day = state.extras.heartRate,
            zones = state.extras.heartRateZones,
            restingHr = state.lastNight()?.restingHr,
            locale = locale,
            zoneId = zoneId,
            onClick = { onOpen(TodayDestination.HeartRate) },
        )

        TodayCard.LAST_WORKOUT -> state.lastWorkout?.let { workout ->
            LastWorkoutCard(workout, state.today, locale, zoneId, onClick = { onOpen(TodayDestination.Session(workout.sessionId)) })
        }

        TodayCard.WEATHER -> WeatherCard(state.extras.weather, locale, weatherAccess)

        else -> Unit
    }
}

@Composable
private fun TileCard(card: TodayCard, state: TodayUiState.Loaded, onOpen: (TodayDestination) -> Unit, modifier: Modifier = Modifier) {
    when (card) {
        TodayCard.SLEEP_SCORE -> SleepTile(state, onClick = { onOpen(TodayDestination.Sleep) }, modifier)

        TodayCard.HRV -> NightlyTile(state, NightlyMetric.HRV, onClick = { onOpen(TodayDestination.Metric(NightlyMetric.HRV)) }, modifier)

        TodayCard.RESTING_HR -> NightlyTile(
            state,
            NightlyMetric.RESTING_HR,
            onClick = { onOpen(TodayDestination.Metric(NightlyMetric.RESTING_HR)) },
            modifier,
        )

        TodayCard.BREATHING_RATE -> NightlyTile(
            state,
            NightlyMetric.BREATHING_RATE,
            onClick = { onOpen(TodayDestination.Metric(NightlyMetric.BREATHING_RATE)) },
            modifier,
        )

        TodayCard.SLEEPING_HR -> NightlyTile(
            state,
            NightlyMetric.SLEEPING_HR,
            onClick = { onOpen(TodayDestination.Metric(NightlyMetric.SLEEPING_HR)) },
            modifier,
        )

        TodayCard.FORM -> FormTile(state, onClick = { onOpen(TodayDestination.Trends) }, modifier)

        TodayCard.WEEK_LOAD -> WeekTile(state, onClick = { onOpen(TodayDestination.Trends) }, modifier)

        TodayCard.VO2MAX -> Vo2MaxTile(state, onClick = { onOpen(TodayDestination.Performance) }, modifier)

        TodayCard.RACE_5K -> RaceTile(state, onClick = { onOpen(TodayDestination.Performance) }, modifier)

        TodayCard.EFFICIENCY -> EfficiencyTile(state, onClick = { onOpen(TodayDestination.Performance) }, modifier)

        TodayCard.OXYGEN_SATURATION -> NightlyTile(
            state,
            NightlyMetric.OXYGEN_SATURATION,
            onClick = { onOpen(TodayDestination.Metric(NightlyMetric.OXYGEN_SATURATION)) },
            modifier,
        )

        TodayCard.SKIN_TEMPERATURE -> NightlyTile(
            state,
            NightlyMetric.SKIN_TEMPERATURE,
            onClick = { onOpen(TodayDestination.Metric(NightlyMetric.SKIN_TEMPERATURE)) },
            modifier,
        )

        else -> Unit
    }
}

@Composable
private fun LastWorkoutCard(
    workout: LastWorkout,
    today: LocalDate,
    locale: Locale,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
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
                RouteMap(routes = workout.routes, contentDescription = stringResource(R.string.workout_map), height = 120.dp)
            }
        }
    }
}

@Composable
private fun SetValuesCard(onOpenPhysiology: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.today_set_values), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenPhysiology) { Text(stringResource(R.string.today_set_values_action)) }
        }
    }
}
