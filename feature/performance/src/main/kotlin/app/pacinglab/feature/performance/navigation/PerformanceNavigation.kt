package app.pacinglab.feature.performance.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.performance.PerformanceRoute
import kotlinx.serialization.Serializable

@Serializable
data object PerformanceKey : NavKey

/** [openWorkout] opens the session that holds a workout, on that workout's tab. */
fun EntryProviderScope<NavKey>.performanceEntries(openWorkout: (String) -> Unit, openSettings: () -> Unit) {
    entry<PerformanceKey> { PerformanceRoute(onOpenWorkout = openWorkout, onOpenSettings = openSettings) }
}
