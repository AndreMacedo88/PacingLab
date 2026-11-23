package app.pacinglab.feature.sleep.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.feature.sleep.SleepRoute
import app.pacinglab.feature.sleep.SleepView
import kotlinx.serialization.Serializable

/**
 * The sleep screen, a top-level destination: [patterns] opens Patterns rather than Night, Night opens on the night
 * ending on [date] (ISO date) or the latest, and [focus] scrolls to the card with that id.
 */
@Serializable
data class SleepKey(val date: String? = null, val patterns: Boolean = false, val focus: String? = null) : NavKey

fun EntryProviderScope<NavKey>.sleepEntries(
    openSettings: () -> Unit,
    openMetric: (NightlyMetric) -> Unit,
    openAlertness: () -> Unit,
    openTest: () -> Unit,
) {
    entry<SleepKey> { key ->
        SleepRoute(
            initialDate = key.date,
            initialView = if (key.patterns) SleepView.PATTERNS else SleepView.NIGHT,
            focus = key.focus,
            onOpenSettings = openSettings,
            onOpenMetric = openMetric,
            onOpenAlertness = openAlertness,
            onTest = openTest,
        )
    }
}
