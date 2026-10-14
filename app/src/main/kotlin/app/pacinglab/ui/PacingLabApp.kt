package app.pacinglab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.pacinglab.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.feature.settings.navigation.DataSourcesKey
import app.pacinglab.feature.settings.navigation.SettingsKey
import app.pacinglab.feature.settings.navigation.settingsEntries
import app.pacinglab.feature.workouts.navigation.workoutsEntries
import app.pacinglab.navigation.TodayKey
import app.pacinglab.navigation.TopLevelDestination
import app.pacinglab.navigation.TrendsKey

/**
 * App frame: navigation bar plus a single back stack whose first entry is always a top-level
 * destination. Selecting a destination replaces the stack, so back from any top-level screen
 * other than Today returns to Today, and back from Today leaves the app.
 */
@Composable
fun PacingLabApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(TodayKey)
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { it.key == backStack.first() }

    BackHandler(enabled = backStack.size == 1 && backStack.first() != TodayKey) {
        backStack.replaceWith(TodayKey)
    }

    Scaffold(
        modifier = modifier,
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
                        label = { Text(stringResource(destination.label)) },
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
                entry<TodayKey> {
                    PlaceholderScreen(
                        title = stringResource(R.string.destination_today),
                        icon = PacingLabIcons.TodaySelected,
                        message = stringResource(R.string.placeholder_today),
                    )
                }
                workoutsEntries(
                    navigateTo = { backStack.add(it) },
                    navigateBack = { backStack.removeLastOrNull() },
                    openDataSources = {
                        backStack.replaceWith(SettingsKey)
                        backStack.add(DataSourcesKey)
                    },
                )
                entry<TrendsKey> {
                    PlaceholderScreen(
                        title = stringResource(R.string.destination_trends),
                        icon = PacingLabIcons.TrendsSelected,
                        message = stringResource(R.string.placeholder_trends),
                    )
                }
                settingsEntries(
                    navigateTo = { backStack.add(it) },
                    navigateBack = { backStack.removeLastOrNull() },
                )
            },
        )
    }
}

private fun NavBackStack<NavKey>.replaceWith(key: NavKey) {
    clear()
    add(key)
}
