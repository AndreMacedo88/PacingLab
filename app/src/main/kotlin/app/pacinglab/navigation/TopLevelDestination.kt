package app.pacinglab.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import app.pacinglab.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.feature.metrics.navigation.BodyKey
import app.pacinglab.feature.performance.navigation.PerformanceKey
import app.pacinglab.feature.sleep.navigation.SleepKey
import app.pacinglab.feature.today.navigation.TodayKey

/**
 * The destinations in the navigation bar, in display order: the briefing, then the training model the app works
 * out, dose (Training) to adaptation (Performance) and response (Body, Sleep). Settings open from each one's app
 * bar. [key] opens a destination at its top; links open it with a focus, which [matches] still recognises.
 */
enum class TopLevelDestination(
    val key: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    TODAY(TodayKey(), R.string.destination_today, PacingLabIcons.Today, PacingLabIcons.TodaySelected),
    TRAINING(TrainingKey(), R.string.destination_training, PacingLabIcons.Training, PacingLabIcons.TrainingSelected),
    PERFORMANCE(PerformanceKey(), R.string.destination_performance, PacingLabIcons.Performance, PacingLabIcons.PerformanceSelected),
    BODY(BodyKey(), R.string.destination_body, PacingLabIcons.Body, PacingLabIcons.BodySelected),
    SLEEP(SleepKey(), R.string.destination_sleep, PacingLabIcons.Sleep, PacingLabIcons.SleepSelected),
    ;

    /** Whether [key] is this destination, opened at its top or at a card. */
    fun matches(key: NavKey?): Boolean = key != null && key::class == this.key::class

    companion object {
        fun of(key: NavKey?): TopLevelDestination? = entries.firstOrNull { it.matches(key) }
    }
}
