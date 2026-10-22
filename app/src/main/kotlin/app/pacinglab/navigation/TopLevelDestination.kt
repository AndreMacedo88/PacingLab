package app.pacinglab.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import app.pacinglab.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.feature.performance.navigation.PerformanceKey
import app.pacinglab.feature.sleep.navigation.SleepKey
import app.pacinglab.feature.today.navigation.TodayKey
import app.pacinglab.feature.trends.navigation.TrendsKey
import app.pacinglab.feature.workouts.navigation.WorkoutsKey

/** The destinations in the navigation bar, in display order. Settings open from each one's app bar. */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayKey, R.string.destination_today, PacingLabIcons.Today, PacingLabIcons.TodaySelected),
    WORKOUTS(WorkoutsKey, R.string.destination_workouts, PacingLabIcons.Workouts, PacingLabIcons.WorkoutsSelected),
    PERFORMANCE(PerformanceKey, R.string.destination_performance, PacingLabIcons.Performance, PacingLabIcons.PerformanceSelected),
    TRENDS(TrendsKey, R.string.destination_trends, PacingLabIcons.Trends, PacingLabIcons.TrendsSelected),
    SLEEP(SleepKey(), R.string.destination_sleep, PacingLabIcons.Sleep, PacingLabIcons.SleepSelected),
}
