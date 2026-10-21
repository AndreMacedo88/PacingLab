package app.pacinglab.feature.today.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.today.TodayRoute
import kotlinx.serialization.Serializable

@Serializable
data object TodayKey : NavKey

fun EntryProviderScope<NavKey>.todayEntries(onOpenSession: (String) -> Unit, onOpenTrends: () -> Unit, onOpenPhysiology: () -> Unit) {
    entry<TodayKey> { TodayRoute(onOpenSession = onOpenSession, onOpenTrends = onOpenTrends, onOpenPhysiology = onOpenPhysiology) }
}
