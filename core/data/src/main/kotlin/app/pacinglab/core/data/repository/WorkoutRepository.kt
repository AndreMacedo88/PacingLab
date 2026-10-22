package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.workout.StreamCleaning
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(private val store: WorkoutStore, private val preferences: UserPreferencesRepository) {
    /** Training sessions, newest first, grouped with the user's merge gap. */
    fun observeSessions(): Flow<List<TrainingSession>> = combine(
        store.observeAll(),
        preferences.preferences.map { it.sessionMergeGap }.distinctUntilChanged(),
    ) { workouts, gap ->
        TrainingSessions.group(workouts, gap).sortedByDescending { it.start }
    }

    fun observeWorkoutCount(): Flow<Int> = store.observeCount()

    fun observeWorkout(id: String): Flow<Workout?> = store.observe(id)

    /** The workout's streams, cleaned of measurement errors (see [StreamCleaning]). */
    suspend fun streams(workout: Workout): WorkoutStreams = store.cleanStreams(workout)
}
