package app.pacinglab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.pacinglab.R
import app.pacinglab.core.data.sync.SyncProblem
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.feature.metrics.navigation.HeartRateDayKey
import app.pacinglab.feature.metrics.navigation.NightlyMetricKey
import app.pacinglab.feature.metrics.navigation.ReadinessKey
import app.pacinglab.feature.metrics.navigation.metricsEntries
import app.pacinglab.feature.performance.navigation.PerformanceKey
import app.pacinglab.feature.performance.navigation.performanceEntries
import app.pacinglab.feature.settings.navigation.DataSourcesKey
import app.pacinglab.feature.settings.navigation.PhysiologyKey
import app.pacinglab.feature.settings.navigation.SettingsKey
import app.pacinglab.feature.settings.navigation.settingsEntries
import app.pacinglab.feature.sleep.navigation.SleepKey
import app.pacinglab.feature.sleep.navigation.sleepEntries
import app.pacinglab.feature.today.TodayDestination
import app.pacinglab.feature.today.navigation.TodayKey
import app.pacinglab.feature.today.navigation.todayEntries
import app.pacinglab.feature.trends.navigation.TrendsKey
import app.pacinglab.feature.trends.navigation.trendsEntries
import app.pacinglab.feature.workouts.navigation.SessionKey
import app.pacinglab.feature.workouts.navigation.workoutsEntries
import app.pacinglab.navigation.TopLevelDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * App frame: navigation bar plus a single back stack whose first entry is always a top-level
 * destination. Selecting a destination replaces the stack, so back from any top-level screen
 * other than Today returns to Today, and back from Today leaves the app. Settings open on top of the
 * current screen from the gear at the right of each top-level app bar.
 */
@Composable
fun PacingLabApp(modifier: Modifier = Modifier, syncProblems: Flow<SyncProblem> = emptyFlow()) {
    val backStack = rememberNavBackStack(TodayKey)
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { it.key == backStack.first() }

    BackHandler(enabled = backStack.size == 1 && backStack.first() != TodayKey) {
        backStack.replaceWith(TodayKey)
    }

    // A pull to refresh that went wrong says why, above the navigation bar, wherever it was pulled.
    val snackbars = remember { SnackbarHostState() }
    val messages = SyncProblem.entries.associateWith { stringResource(it.message()) }
    val actions = SyncProblem.entries.associateWith { problem -> problem.action()?.let { stringResource(it) } }
    LaunchedEffect(syncProblems) {
        syncProblems.collect { problem ->
            val result = snackbars.showSnackbar(messages.getValue(problem), actions[problem], duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) backStack.add(DataSourcesKey)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        // Each screen draws its own top app bar and handles the status bar inset itself; the frame
        // only reserves space for the navigation bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = destination == currentTopLevel
                    NavigationBarItem(
                        selected = selected,
                        onClick = { if (!selected || backStack.size > 1) backStack.replaceWith(destination.key) },
                        icon = {
                            Icon(
                                painter = painterResource(if (selected) destination.selectedIcon else destination.icon),
                                contentDescription = null,
                            )
                        },
                        // Five tabs leave about 77 dp each on a phone; "Performance" would wrap, so a long label shrinks instead.
                        label = {
                            Text(
                                text = stringResource(destination.label),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = 8.sp,
                                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                                ),
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                val openSettings: () -> Unit = { backStack.add(SettingsKey) }
                todayEntries(
                    onOpen = { destination ->
                        when (destination) {
                            is TodayDestination.Session -> backStack.add(SessionKey(destination.id))
                            is TodayDestination.Metric -> backStack.add(NightlyMetricKey(destination.metric.name))
                            TodayDestination.Readiness -> backStack.add(ReadinessKey)
                            TodayDestination.HeartRate -> backStack.add(HeartRateDayKey)
                            TodayDestination.Sleep -> backStack.replaceWith(SleepKey())
                            TodayDestination.Trends -> backStack.replaceWith(TrendsKey)
                            TodayDestination.Performance -> backStack.replaceWith(PerformanceKey)
                            TodayDestination.Physiology -> backStack.add(PhysiologyKey)
                        }
                    },
                    onOpenSettings = openSettings,
                )
                metricsEntries(navigateBack = { backStack.removeLastOrNull() })
                sleepEntries(openSettings = openSettings, openMetric = { backStack.add(NightlyMetricKey(it.name)) })
                workoutsEntries(
                    navigateTo = { backStack.add(it) },
                    navigateBack = { backStack.removeLastOrNull() },
                    openDataSources = { backStack.add(DataSourcesKey) },
                    openPhysiology = { backStack.add(PhysiologyKey) },
                    openSettings = openSettings,
                )
                performanceEntries(openWorkout = { backStack.add(SessionKey(it)) }, openSettings = openSettings)
                trendsEntries(
                    openPhysiology = { backStack.add(PhysiologyKey) },
                    openSettings = openSettings,
                    openSession = { backStack.add(SessionKey(it)) },
                )
                settingsEntries(
                    navigateTo = { backStack.add(it) },
                    navigateBack = { backStack.removeLastOrNull() },
                )
            },
        )
    }
}

private fun SyncProblem.message(): Int = when (this) {
    SyncProblem.NO_ACCESS -> R.string.sync_no_access
    SyncProblem.RATE_LIMITED -> R.string.sync_rate_limited
    SyncProblem.HEALTH_CONNECT_FAILED -> R.string.sync_failed
    SyncProblem.GOOGLE_HEALTH_SIGN_IN -> R.string.sync_google_sign_in
    SyncProblem.GOOGLE_HEALTH_UNREACHABLE -> R.string.sync_google_unreachable
}

/** The button that opens Data sources, where each problem can be dealt with; none when waiting is all it takes. */
private fun SyncProblem.action(): Int? = when (this) {
    SyncProblem.NO_ACCESS -> R.string.sync_set_up
    SyncProblem.HEALTH_CONNECT_FAILED -> R.string.sync_details
    SyncProblem.GOOGLE_HEALTH_SIGN_IN -> R.string.sync_sign_in
    SyncProblem.RATE_LIMITED, SyncProblem.GOOGLE_HEALTH_UNREACHABLE -> null
}

private fun NavBackStack<NavKey>.replaceWith(key: NavKey) {
    clear()
    add(key)
}
