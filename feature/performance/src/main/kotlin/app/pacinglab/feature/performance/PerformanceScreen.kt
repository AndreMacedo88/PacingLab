package app.pacinglab.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.SettingsAction
import app.pacinglab.core.designsystem.format.Formats
import java.time.LocalDate
import kotlin.math.roundToLong

@Composable
fun PerformanceRoute(
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
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
        onOpenSettings = onOpenSettings,
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
    )
}

/**
 * What the athlete can do: VO2max, race predictions, critical speed, best efforts, aerobic efficiency
 * and decoupling. Training load and recovery live on Trends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(
    state: PerformanceUiState,
    onOpenWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSaveTest: (LocalDate, Double) -> Unit = { _, _ -> },
    onDeleteTest: (LocalDate) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    initialEffortRange: EffortRange = EffortRange.RECENT,
) {
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.performance_title)) }, actions = { SettingsAction(onOpenSettings) }) },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            when (state) {
                PerformanceUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is PerformanceUiState.Loaded -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val running = state.running
                    item(key = "vo2max") { Vo2MaxCard(state.vo2max, state.today, onSaveTest, onDeleteTest, locale) }
                    if (running.predictions.any { it.fromVo2Max != null || it.fromCriticalSpeed != null }) {
                        item(key = "races") { RaceCard(running.predictions, running.vo2max, locale) }
                    }
                    item(key = "critical-speed") { CriticalSpeedCard(running.criticalSpeed, onOpenWorkout, locale) }
                    if (!running.allTime.isEmpty()) {
                        item(key = "efforts") {
                            BestEffortsCard(running.recent, running.allTime, initialEffortRange, onOpenWorkout, locale)
                        }
                    }
                    if (state.efficiency.trend.isNotEmpty()) {
                        item(key = "efficiency") { EfficiencyCard(state.efficiency, state.today, locale) }
                    }
                    if (running.decoupling.isNotEmpty()) {
                        item(key = "decoupling") { DecouplingCard(running.decoupling, onOpenWorkout, locale) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PerformanceCard(title: String, info: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = title, info = info)
            content()
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

/** Pace "3:47" for [meters] in [seconds]. */
internal fun pace(seconds: Double, meters: Double): String = Formats.pacePerKm((seconds * 1000).roundToLong(), meters) ?: "–"
