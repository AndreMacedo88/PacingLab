package app.pacinglab.feature.sleep.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.feature.sleep.SleepRoute
import kotlinx.serialization.Serializable

/** The sleep screen, opened on the night ending on [date] (ISO date), or the latest night. */
@Serializable
data class SleepKey(val date: String? = null) : NavKey

fun EntryProviderScope<NavKey>.sleepEntries(navigateBack: () -> Unit) {
    entry<SleepKey> { key -> SleepRoute(initialDate = key.date, onBack = navigateBack) }
}
