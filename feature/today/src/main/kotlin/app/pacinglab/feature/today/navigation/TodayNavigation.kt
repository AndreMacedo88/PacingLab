package app.pacinglab.feature.today.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.today.TodayDestination
import app.pacinglab.feature.today.TodayRoute
import kotlinx.serialization.Serializable

/** Today, a top-level destination, scrolled to the card with the id [focus] when a link or the search asks for one. */
@Serializable
data class TodayKey(val focus: String? = null) : NavKey

/** [onOpen] leads to the screen a card stands for; the app maps each destination to its route. */
fun EntryProviderScope<NavKey>.todayEntries(onOpen: (TodayDestination) -> Unit, onOpenSettings: () -> Unit) {
    entry<TodayKey> { key -> TodayRoute(onOpen = onOpen, onOpenSettings = onOpenSettings, focus = key.focus) }
}
