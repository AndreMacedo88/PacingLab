package app.pacinglab.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.settings.AboutScreen
import app.pacinglab.feature.settings.SettingsScreen
import app.pacinglab.feature.settings.SettingsSection
import app.pacinglab.feature.settings.SettingsSectionRoute
import app.pacinglab.feature.settings.datasources.DataSourcesRoute
import app.pacinglab.feature.settings.physiology.PhysiologyRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsKey : NavKey

/** A page of settings under the settings list. */
@Serializable
data class SettingsSectionKey(val section: SettingsSection) : NavKey

@Serializable
data object DataSourcesKey : NavKey

@Serializable
data object PhysiologyKey : NavKey

@Serializable
data object AboutKey : NavKey

/** Registers the settings screens with the app's navigation. */
fun EntryProviderScope<NavKey>.settingsEntries(navigateTo: (NavKey) -> Unit, navigateBack: () -> Unit) {
    entry<SettingsKey> {
        SettingsScreen(
            onOpenSection = { navigateTo(SettingsSectionKey(it)) },
            onOpenAbout = { navigateTo(AboutKey) },
            onBack = navigateBack,
        )
    }
    entry<SettingsSectionKey> { key ->
        SettingsSectionRoute(
            section = key.section,
            onOpenPhysiology = { navigateTo(PhysiologyKey) },
            onOpenDataSources = { navigateTo(DataSourcesKey) },
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
