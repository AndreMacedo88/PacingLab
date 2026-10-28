package app.pacinglab.feature.metrics.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.feature.metrics.HeartRateDayRoute
import app.pacinglab.feature.metrics.NightlyMetricRoute
import app.pacinglab.feature.metrics.ReadinessRoute
import kotlinx.serialization.Serializable

/** The detail page of a nightly measure; [metric] is a [NightlyMetric] name. */
@Serializable
data class NightlyMetricKey(val metric: String) : NavKey

@Serializable
data object ReadinessKey : NavKey

@Serializable
data object HeartRateDayKey : NavKey

fun EntryProviderScope<NavKey>.metricsEntries(navigateBack: () -> Unit) {
    entry<NightlyMetricKey> { key -> NightlyMetricRoute(NightlyMetric.valueOf(key.metric), onBack = navigateBack) }
    entry<ReadinessKey> { ReadinessRoute(onBack = navigateBack) }
    entry<HeartRateDayKey> { HeartRateDayRoute(onBack = navigateBack) }
}
