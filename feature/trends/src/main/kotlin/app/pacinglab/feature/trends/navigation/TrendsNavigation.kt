package app.pacinglab.feature.trends.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.trends.TrendsRoute
import kotlinx.serialization.Serializable

@Serializable
data object TrendsKey : NavKey

fun EntryProviderScope<NavKey>.trendsEntries(openPhysiology: () -> Unit, openSettings: () -> Unit) {
    entry<TrendsKey> { TrendsRoute(onOpenPhysiology = openPhysiology, onOpenSettings = openSettings) }
}
