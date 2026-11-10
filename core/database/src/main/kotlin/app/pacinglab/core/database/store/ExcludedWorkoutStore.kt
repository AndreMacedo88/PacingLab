package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.ExcludedWorkoutEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists which workouts the athlete left out of the fitness estimates, by workout id. */
@Singleton
class ExcludedWorkoutStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.excludedWorkoutDao()

    fun observe(): Flow<Set<String>> = dao.observeIds().map { it.toSet() }.distinctUntilChanged()

    suspend fun exclude(workoutIds: Collection<String>) {
        if (workoutIds.isNotEmpty()) dao.upsert(workoutIds.map(::ExcludedWorkoutEntity))
    }

    suspend fun include(workoutIds: Collection<String>) {
        if (workoutIds.isNotEmpty()) dao.delete(workoutIds.toList())
    }
}
