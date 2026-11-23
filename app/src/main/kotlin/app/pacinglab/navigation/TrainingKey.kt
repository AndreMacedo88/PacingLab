package app.pacinglab.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The views of Training: the sessions as a list or a calendar, and what they add up to. */
enum class TrainingView { SESSIONS, CALENDAR, LOAD }

/**
 * Training, a top-level destination, on [view]; [focus] scrolls Load to the card with that id. Training composes
 * the sessions of feature/workouts and the load of feature/trends, so its key lives with the app.
 */
@Serializable
data class TrainingKey(val view: TrainingView = TrainingView.SESSIONS, val focus: String? = null) : NavKey
