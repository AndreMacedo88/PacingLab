package app.pacinglab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import app.pacinglab.R
import app.pacinglab.core.data.sync.SyncProblem
import app.pacinglab.core.designsystem.component.DashboardHost
import app.pacinglab.core.designsystem.component.LocalDashboardHost
import app.pacinglab.core.designsystem.component.LocalSharedScreens
import app.pacinglab.core.designsystem.component.SharedScreens
import app.pacinglab.feature.metrics.navigation.AlertnessKey
import app.pacinglab.feature.metrics.navigation.BodyKey
import app.pacinglab.feature.metrics.navigation.HeartRateDayKey
import app.pacinglab.feature.metrics.navigation.NightlyMetricKey
import app.pacinglab.feature.metrics.navigation.ReactionTestKey
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
import app.pacinglab.feature.today.TodayTab
import app.pacinglab.feature.today.navigation.TodayKey
import app.pacinglab.feature.today.navigation.todayEntries
import app.pacinglab.feature.workouts.navigation.SessionKey
import app.pacinglab.feature.workouts.navigation.workoutsEntries
import app.pacinglab.navigation.TopLevelDestination
import app.pacinglab.navigation.TrainingKey
import app.pacinglab.navigation.TrainingView
import app.pacinglab.search.SearchKey
import app.pacinglab.search.SearchScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * App frame: navigation bar plus a single back stack whose first entry is always a top-level
 * destination. Selecting a destination replaces the stack, so back from any top-level screen
 * other than Today returns to Today, and back from Today leaves the app. Settings open on top of the
 * current screen from the gear at the right of each top-level app bar, the search from the magnifier beside it.
 * Summaries open the card they stand for on its own tab, scrolled to it; a long press on a card can pin it to
 * Today, which a snackbar confirms.
 */
@Composable
fun PacingLabApp(modifier: Modifier = Modifier, syncProblems: Flow<SyncProblem> = emptyFlow(), shell: ShellViewModel = hiltViewModel()) {
    val backStack = rememberNavBackStack(TodayKey())
    val currentTopLevel = TopLevelDestination.of(backStack.firstOrNull())

    BackHandler(enabled = backStack.size == 1 && !TopLevelDestination.TODAY.matches(backStack.first())) {
        backStack.replaceWith(TodayKey())
    }

    // A pull to refresh that went wrong says why, above the navigation bar, wherever it was pulled.
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messages = SyncProblem.entries.associateWith { stringResource(it.message()) }
    val actions = SyncProblem.entries.associateWith { problem -> problem.action()?.let { stringResource(it) } }
    LaunchedEffect(syncProblems) {
        syncProblems.collect { problem ->
            val result = snackbars.showSnackbar(messages.getValue(problem), actions[problem], duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) backStack.add(DataSourcesKey)
        }
    }

    val onToday by shell.onToday.collectAsStateWithLifecycle()
    val pinned = stringResource(R.string.pinned_to_today)
    val removed = stringResource(R.string.removed_from_today)
    val host = DashboardHost(
        onSearch = { backStack.add(SearchKey) },
        onToday = onToday,
        onPin = { id, pin ->
            shell.pin(id, pin)
            scope.launch { snackbars.showSnackbar(if (pin) pinned else removed) }
        },
    )

    CompositionLocalProvider(LocalDashboardHost provides host) {
        Scaffold(
            modifier = modifier,
            snackbarHost = { SnackbarHost(snackbars) },
            // Each screen draws its own top app bar and handles the status bar inset itself; the frame
            // only reserves space for the navigation bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // The reaction test takes taps anywhere on the screen, so nothing else may sit under a thumb; the
                // search has the whole screen for its results.
                val last = backStack.lastOrNull()
                if (last != ReactionTestKey && last != SearchKey) {
                    Destinations(currentTopLevel) { destination ->
                        if (destination != currentTopLevel || backStack.size > 1) backStack.replaceWith(destination.key)
                    }
                }
            },
        ) { padding ->
            SharedTransitionLayout(
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                val transition = this
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                        remember(transition) { sharedScreens(transition) },
                    ),
                    sharedTransitionScope = transition,
                    transitionSpec = { forward() },
                    popTransitionSpec = { backward() },
                    predictivePopTransitionSpec = { backward() },
                    entryProvider = entryProvider {
                        val openSettings: () -> Unit = { backStack.add(SettingsKey) }
                        val openSession: (String) -> Unit = { backStack.add(SessionKey(it)) }
                        val openPhysiology: () -> Unit = { backStack.add(PhysiologyKey) }
                        todayEntries(
                            onOpen = { destination ->
                                when (destination) {
                                    is TodayDestination.Session -> openSession(destination.id)
                                    is TodayDestination.Metric -> backStack.add(NightlyMetricKey(destination.metric.name))
                                    TodayDestination.Readiness -> backStack.add(ReadinessKey)
                                    TodayDestination.HeartRate -> backStack.add(HeartRateDayKey)
                                    TodayDestination.Physiology -> openPhysiology()
                                    TodayDestination.Alertness -> backStack.add(AlertnessKey)
                                    TodayDestination.ReactionTest -> backStack.add(ReactionTestKey)
                                    is TodayDestination.Tab -> backStack.replaceWith(destination.key())
                                }
                            },
                            onOpenSettings = openSettings,
                        )
                        metricsEntries(navigateTo = {
                            backStack.add(it)
                        }, navigateBack = { backStack.removeLastOrNull() }, openSettings = openSettings)
                        sleepEntries(
                            openSettings = openSettings,
                            openMetric = { backStack.add(NightlyMetricKey(it.name)) },
                            openAlertness = { backStack.add(AlertnessKey) },
                            openTest = { backStack.add(ReactionTestKey) },
                        )
                        entry<TrainingKey> { key ->
                            TrainingHost(
                                key = key,
                                onOpenSession = openSession,
                                onOpenDataSources = { backStack.add(DataSourcesKey) },
                                onOpenPhysiology = openPhysiology,
                                onOpenSettings = openSettings,
                            )
                        }
                        workoutsEntries(navigateBack = { backStack.removeLastOrNull() }, openPhysiology = openPhysiology)
                        performanceEntries(openWorkout = openSession, openPhysiology = openPhysiology, openSettings = openSettings)
                        settingsEntries(
                            navigateTo = { backStack.add(it) },
                            navigateBack = { backStack.removeLastOrNull() },
                        )
                        entry<SearchKey> {
                            SearchScreen(
                                onOpen = { target ->
                                    // A tab starts the stack again; a page opens over the search, so back returns to the results.
                                    if (TopLevelDestination.of(target) != null) backStack.replaceWith(target) else backStack.add(target)
                                },
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Destinations(current: TopLevelDestination?, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
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
}

/** Gives every screen the scopes its shared elements move in: the frame's and the one animating that screen. */
private fun sharedScreens(transition: SharedTransitionScope) = NavEntryDecorator<NavKey>(
    decorate = { entry ->
        CompositionLocalProvider(LocalSharedScreens provides SharedScreens(transition, LocalNavAnimatedContentScope.current)) {
            entry.Content()
        }
    },
)

/** The tab and card a summary on Today stands for. */
private fun TodayDestination.Tab.key(): NavKey = when (tab) {
    TodayTab.LOAD -> TrainingKey(TrainingView.LOAD, focus)
    TodayTab.PERFORMANCE -> PerformanceKey(focus)
    TodayTab.BODY -> BodyKey(focus)
    TodayTab.SLEEP_NIGHT -> SleepKey(focus = focus)
    TodayTab.SLEEP_PATTERNS -> SleepKey(patterns = true, focus = focus)
}

/**
 * Moving forward: between tabs the screens fade through each other (the stack starts again); a page opened on top
 * slides in a little from the side while the one under it fades.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.forward(): ContentTransform = if (targetState.previousEntries.isEmpty()) {
    fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS)) togetherWith fadeOut(tween(FADE_OUT_MILLIS))
} else {
    (slideInHorizontally(tween(SLIDE_MILLIS)) { it / SLIDE_FRACTION } + fadeIn(tween(SLIDE_MILLIS))) togetherWith
        (slideOutHorizontally(tween(SLIDE_MILLIS)) { -it / SLIDE_FRACTION } + fadeOut(tween(SLIDE_MILLIS)))
}

/** Going back, the same motion the other way; a predictive back gesture runs it under the finger. */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.backward(): ContentTransform =
    (slideInHorizontally(tween(SLIDE_MILLIS)) { -it / SLIDE_FRACTION } + fadeIn(tween(SLIDE_MILLIS))) togetherWith
        (slideOutHorizontally(tween(SLIDE_MILLIS)) { it / SLIDE_FRACTION } + fadeOut(tween(SLIDE_MILLIS)))

private const val FADE_IN_MILLIS = 210
private const val FADE_OUT_MILLIS = 90
private const val SLIDE_MILLIS = 300

/** A page slides in by a tenth of the screen's width, enough to say "on top" without sweeping the whole screen. */
private const val SLIDE_FRACTION = 10

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
