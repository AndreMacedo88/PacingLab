package app.pacinglab.feature.performance.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.performance.PerformanceRoute
import kotlinx.serialization.Serializable

/** Performance, a top-level destination, scrolled to the card with the id [focus] when a link or the search asks for one. */
@Serializable
data class PerformanceKey(val focus: String? = null) : NavKey

/** [openWorkout] opens the session that holds a workout, on that workout's tab. */
fun EntryProviderScope<NavKey>.performanceEntries(openWorkout: (String) -> Unit, openPhysiology: () -> Unit, openSettings: () -> Unit) {
    entry<PerformanceKey> { key ->
        PerformanceRoute(onOpenWorkout = openWorkout, onOpenPhysiology = openPhysiology, onOpenSettings = openSettings, focus = key.focus)
    }
}
