package app.pacinglab.feature.today.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.today.TodayDestination
import app.pacinglab.feature.today.TodayRoute
import kotlinx.serialization.Serializable

@Serializable
data object TodayKey : NavKey

/** [onOpen] leads to the screen a card stands for; the app maps each destination to its route. */
fun EntryProviderScope<NavKey>.todayEntries(onOpen: (TodayDestination) -> Unit, onOpenSettings: () -> Unit) {
    entry<TodayKey> { TodayRoute(onOpen = onOpen, onOpenSettings = onOpenSettings) }
}
