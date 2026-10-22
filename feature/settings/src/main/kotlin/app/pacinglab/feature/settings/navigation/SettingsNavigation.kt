package app.pacinglab.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.settings.AboutScreen
import app.pacinglab.feature.settings.SettingsRoute
import app.pacinglab.feature.settings.datasources.DataSourcesRoute
import app.pacinglab.feature.settings.physiology.PhysiologyRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsKey : NavKey

@Serializable
data object DataSourcesKey : NavKey

@Serializable
data object PhysiologyKey : NavKey

@Serializable
data object AboutKey : NavKey

/** Registers the settings screens with the app's navigation. */
fun EntryProviderScope<NavKey>.settingsEntries(navigateTo: (NavKey) -> Unit, navigateBack: () -> Unit) {
    entry<SettingsKey> {
        SettingsRoute(
            onOpenPhysiology = { navigateTo(PhysiologyKey) },
            onOpenDataSources = { navigateTo(DataSourcesKey) },
            onOpenAbout = { navigateTo(AboutKey) },
            onBack = navigateBack,
        )
    }
    entry<PhysiologyKey> {
        PhysiologyRoute(onBack = navigateBack)
    }
    entry<DataSourcesKey> {
        DataSourcesRoute(onBack = navigateBack)
    }
    entry<AboutKey> {
        AboutScreen(onBack = navigateBack)
    }
}
