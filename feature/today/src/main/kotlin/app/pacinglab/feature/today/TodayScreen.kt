package app.pacinglab.feature.today

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
import androidx.compose.runtime.remember
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
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.chart.HeartRateChart
import app.pacinglab.core.designsystem.chart.MiniBars
import app.pacinglab.core.designsystem.chart.SegmentBar
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.CardHeader
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

@Composable
fun TodayRoute(
    onOpenSession: (String) -> Unit,
    onOpenTrends: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenPhysiology: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    TodayScreen(
        state,
        onOpenSession,
        onOpenTrends,
        onOpenPhysiology,
        modifier,
        onOpenSleep = onOpenSleep,
        onOpenSettings = onOpenSettings,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        onRate = viewModel::rate,
        onRateGym = viewModel::rateGym,
    )
}

/**
 * The day at a glance: readiness first, then small tiles for last night and recent training, each with
 * a mini chart of the last days, and the latest workout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    onOpenSession: (String) -> Unit,
    onOpenTrends: () -> Unit,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onOpenSleep: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRate: (Int) -> Unit = {},
    onRateGym: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
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
                },
                actions = { SettingsAction(onOpenSettings) },
            )
        },
    ) { scaffoldPadding ->
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
                        item(key = "values") { SetValuesCard(onOpenPhysiology) }
                    }
                    item(key = "readiness") { ReadinessCard(state.readiness, state.readinessHistory, state.days(locale)) }
                    item(key = "rested") { RestedCard(state.rested, onRate) }
                    state.gymToRate?.let { gym ->
                        item(key = "gym") { GymEffortCard(gym, state.today, zoneId, onRate = { onRateGym(gym.workoutIds, it) }) }
                    }
                    item(key = "night") {
                        TileRow {
                            SleepTile(state, onOpenSleep, Modifier.weight(1f))
                            HrvTile(state, Modifier.weight(1f))
                        }
                    }
                    item(key = "heart") {
                        TileRow {
                            RestingHrTile(state, Modifier.weight(1f))
                            BreathingTile(state, Modifier.weight(1f))
                        }
                    }
                    item(key = "training") {
                        TileRow {
                            FormTile(state, onOpenTrends, Modifier.weight(1f))
                            WeekTile(state, onOpenTrends, Modifier.weight(1f))
                        }
                    }
                    state.lastWorkout?.let { workout ->
                        item(key = "workout") {
                            LastWorkoutCard(workout, state.today, locale, zoneId, onClick = { onOpenSession(workout.sessionId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A small card: label, headline value, one line of context and a mini chart. */
@Composable
private fun Tile(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chart: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers(), maxLines = 1)
            Text(
                text = detail ?: "",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(top = 8.dp)) { chart() }
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxHeight(), colors = colors) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxHeight(), colors = colors) { content() }
    }
}

/** One value per day of the last week, oldest first, null for days without a night. */
private fun TodayUiState.Loaded.byNight(value: (Night) -> Double?): List<Double?> {
    val nights = nights.associateBy { it.date }
    return (TodayViewModel.TILE_DAYS - 1 downTo 0).map { back -> nights[today.minusDays(back.toLong())]?.let(value) }
}

/** Weekday initials of the days [byNight] covers. */
private fun TodayUiState.Loaded.days(locale: Locale): List<String> =
    weekdayInitials((TodayViewModel.TILE_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }, locale)

private fun TodayUiState.Loaded.lastNight(): Night? = nights.lastOrNull()?.takeIf { it.date == today }

@Composable
private fun SleepTile(state: TodayUiState.Loaded, onOpenSleep: () -> Unit, modifier: Modifier = Modifier) {
    val night = state.lastNight()
    val score = night?.let { state.sleepScores[it.date] }
    val colors = chartColors()
    Tile(
        label = stringResource(R.string.tile_sleep),
        value = score?.score?.toString() ?: DASH,
        detail = if (night != null && score != null) {
            stringResource(R.string.tile_sleep_detail, stringResource(score.rating.label()), Formats.hoursMinutes(night.asleepMillis))
        } else {
            stringResource(R.string.tile_no_night)
        },
        modifier = modifier,
        onClick = onOpenSleep,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (night != null && night.stageMillis.isNotEmpty()) {
                // Deep, REM, light, awake: the order the colours were checked in.
                SegmentBar(
                    weights = listOf(SleepStageType.DEEP, SleepStageType.REM, SleepStageType.LIGHT, SleepStageType.AWAKE)
                        .map { (night.stageMillis[it] ?: 0L).toDouble() },
                    colors = listOf(colors.deepSleep, colors.remSleep, colors.lightSleep, colors.awake),
                )
            }
            Sparkline(
                values = state.byNight { state.sleepScores[it.date]?.score?.toDouble() },
                band = SleepScore.GOOD_FROM.toDouble()..100.0,
                labels = state.days(LocalConfiguration.current.locales[0]),
                height = 32.dp,
            )
        }
    }
}

internal fun SleepRating.label(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_good
    SleepRating.FAIR -> R.string.sleep_fair
    SleepRating.POOR -> R.string.sleep_poor
}

@Composable
private fun HrvTile(state: TodayUiState.Loaded, modifier: Modifier = Modifier) {
    val hrv = state.readiness.hrv
    val lastNight = state.lastNight()?.hrvMs
    Tile(
        label = stringResource(R.string.tile_hrv),
        value = lastNight?.let { stringResource(R.string.unit_ms, it.roundToInt()) } ?: DASH,
        detail = hrv?.let { stringResource(R.string.tile_hrv_range, it.normalLowMs.roundToInt(), it.normalHighMs.roundToInt()) }
            ?: pluralStringResource(
                R.plurals.tile_hrv_baseline,
                Readiness.BASELINE_NIGHTS,
                state.readiness.baselineNights,
                Readiness.BASELINE_NIGHTS,
            ),
        modifier = modifier,
    ) {
        Sparkline(
            values = state.byNight { it.hrvMs },
            bands = state.readinessHistory.map { day -> day.hrv?.let { it.normalLowMs..it.normalHighMs } },
            labels = state.days(LocalConfiguration.current.locales[0]),
        )
    }
}

@Composable
private fun RestingHrTile(state: TodayUiState.Loaded, modifier: Modifier = Modifier) {
    val resting = state.readiness.restingHr
    val lastNight = state.lastNight()?.restingHr
    Tile(
        label = stringResource(R.string.tile_resting_hr),
        value = lastNight?.let { stringResource(R.string.unit_bpm, it.roundToInt()) } ?: DASH,
        detail = if (resting != null) {
            stringResource(R.string.tile_usual_bpm, resting.usual.roundToInt())
        } else {
            pluralStringResource(
                R.plurals.tile_usual_pending,
                Readiness.MIN_SHORT_BASELINE,
                state.nights.count {
                    it.restingHr != null
                },
                Readiness.MIN_SHORT_BASELINE,
            )
        },
        modifier = modifier,
    ) {
        Sparkline(
            values = state.byNight { it.restingHr },
            color = chartColors().restingHr,
            bands = state.readinessHistory.map { day -> day.restingHr?.let { it.normalLow..it.normalHigh } },
            labels = state.days(LocalConfiguration.current.locales[0]),
        )
    }
}

@Composable
private fun BreathingTile(state: TodayUiState.Loaded, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val lastNight = state.lastNight()?.respiratoryRate
    val earlier = state.nights.filter { it.date < state.today }.mapNotNull { it.respiratoryRate }
    Tile(
        label = stringResource(R.string.tile_breathing),
        value = lastNight?.let { stringResource(R.string.unit_breaths, "%.1f".format(locale, it)) } ?: DASH,
        detail = earlier.takeIf {
            it.isNotEmpty()
        }?.let { stringResource(R.string.tile_usual_breaths, "%.1f".format(locale, it.average())) },
        modifier = modifier,
    ) {
        Sparkline(
            values = state.byNight { it.respiratoryRate },
            color = chartColors().series[2],
            bands = state.readinessHistory.map { day ->
                day.breathingUsual?.let { it - Readiness.BREATHING_MARGIN..it + Readiness.BREATHING_MARGIN }
            },
            labels = state.days(locale),
        )
    }
}

@Composable
private fun FormTile(state: TodayUiState.Loaded, onOpenTrends: () -> Unit, modifier: Modifier = Modifier) {
    val today = state.form.lastOrNull()
    Tile(
        label = stringResource(R.string.tile_form),
        value = today?.let { signed(it.form) } ?: DASH,
        detail = today?.let { stringResource(R.string.tile_form_detail, it.fitness.roundToInt(), it.fatigue.roundToInt()) },
        modifier = modifier,
        onClick = onOpenTrends,
    ) {
        Sparkline(
            values = state.form.map { it.form },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            bands = state.form.map { Readiness.formBand(it.fitness).let { band -> -band..band } },
            baseline = 0.0,
        )
    }
}

@Composable
private fun WeekTile(state: TodayUiState.Loaded, onOpenTrends: () -> Unit, modifier: Modifier = Modifier) {
    val week = state.week
    val today = state.today.dayOfWeek.value - 1
    Tile(
        label = stringResource(R.string.tile_week),
        value = week?.load?.roundToInt()?.toString() ?: "0",
        detail = week?.let {
            stringResource(R.string.tile_week_detail, Formats.hoursMinutes(it.durationMillis)) + " · " +
                pluralStringResource(R.plurals.sessions, state.weekSessions, state.weekSessions)
        },
        modifier = modifier,
        onClick = onOpenTrends,
    ) {
        // Days still to come stay empty rather than showing zero.
        MiniBars(
            values = state.weekLoads.mapIndexed { i, load -> if (i > today) null else load },
            color = chartColors().series[0],
            labels = weekdayInitials(
                (0 until 7).map {
                    state.today.minusDays((today - it).toLong())
                },
                LocalConfiguration.current.locales[0],
            ),
            today = today,
        )
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

private const val HOUR = 3_600_000.0
private const val DASH = "–"
