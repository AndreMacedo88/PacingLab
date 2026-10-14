package app.pacinglab.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import app.pacinglab.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.feature.settings.navigation.SettingsKey
import app.pacinglab.feature.workouts.navigation.WorkoutsKey
import kotlinx.serialization.Serializable

@Serializable
data object TodayKey : NavKey

@Serializable
data object TrendsKey : NavKey

/** The destinations in the navigation bar, in display order. */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayKey, R.string.destination_today, PacingLabIcons.Today, PacingLabIcons.TodaySelected),
    WORKOUTS(WorkoutsKey, R.string.destination_workouts, PacingLabIcons.Workouts, PacingLabIcons.WorkoutsSelected),
    TRENDS(TrendsKey, R.string.destination_trends, PacingLabIcons.Trends, PacingLabIcons.TrendsSelected),
    SETTINGS(SettingsKey, R.string.destination_settings, PacingLabIcons.Settings, PacingLabIcons.SettingsSelected),
}
