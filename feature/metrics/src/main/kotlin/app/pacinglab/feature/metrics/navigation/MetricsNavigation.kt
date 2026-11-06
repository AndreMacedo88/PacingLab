package app.pacinglab.feature.metrics.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.feature.metrics.AlertnessRoute
import app.pacinglab.feature.metrics.HeartRateDayRoute
import app.pacinglab.feature.metrics.NightlyMetricRoute
import app.pacinglab.feature.metrics.ReactionTestRoute
import app.pacinglab.feature.metrics.ReadinessRoute
import kotlinx.serialization.Serializable

/** The detail page of a nightly measure; [metric] is a [NightlyMetric] name. */
@Serializable
data class NightlyMetricKey(val metric: String) : NavKey

@Serializable
data object ReadinessKey : NavKey

@Serializable
data object HeartRateDayKey : NavKey

/** Alertness through the day, the reaction tests and how they tune it. */
@Serializable
data object AlertnessKey : NavKey

/** The 3-minute reaction test. */
@Serializable
data object ReactionTestKey : NavKey

fun EntryProviderScope<NavKey>.metricsEntries(navigateBack: () -> Unit, openTest: () -> Unit) {
    entry<NightlyMetricKey> { key -> NightlyMetricRoute(NightlyMetric.valueOf(key.metric), onBack = navigateBack) }
    entry<ReadinessKey> { ReadinessRoute(onBack = navigateBack) }
    entry<HeartRateDayKey> { HeartRateDayRoute(onBack = navigateBack) }
    entry<AlertnessKey> { AlertnessRoute(onBack = navigateBack, onTest = openTest) }
    entry<ReactionTestKey> { ReactionTestRoute(onBack = navigateBack) }
}
