package app.pacinglab.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.settings.SettingsScreen
import app.pacinglab.feature.settings.datasources.DataSourcesRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsKey : NavKey

@Serializable
data object DataSourcesKey : NavKey

/** Registers the settings screens with the app's navigation. */
fun EntryProviderScope<NavKey>.settingsEntries(navigateTo: (NavKey) -> Unit, navigateBack: () -> Unit) {
    entry<SettingsKey> {
        SettingsScreen(onOpenDataSources = { navigateTo(DataSourcesKey) })
    }
    entry<DataSourcesKey> {
        DataSourcesRoute(onBack = navigateBack)
    }
}
