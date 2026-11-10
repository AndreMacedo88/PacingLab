package app.pacinglab.core.data.repository

import app.pacinglab.core.database.store.ExcludedWorkoutStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Workouts the athlete left out of the fitness estimates because their heart rate does not reflect the
 * effort: a run spent talking, a day of illness, a faulty reading. Such a workout gives no VO2max (neither
 * PacingLab's estimate nor Fitbit's value of that day), no threshold or maximal heart rate, no aerobic
 * efficiency or decoupling, no critical speed effort and no heart rate recovery for the trend. Its load,
 * time in zones, kind and best efforts still count: the work was done.
 */
@Singleton
class ExcludedWorkoutRepository @Inject constructor(private val store: ExcludedWorkoutStore) {
    /** Ids of the workouts left out. */
    fun observe(): Flow<Set<String>> = store.observe()

    /** Leaves [workoutIds] out of the estimates, or brings them back when [excluded] is false. */
    suspend fun setExcluded(workoutIds: Collection<String>, excluded: Boolean) =
        if (excluded) store.exclude(workoutIds) else store.include(workoutIds)
}
