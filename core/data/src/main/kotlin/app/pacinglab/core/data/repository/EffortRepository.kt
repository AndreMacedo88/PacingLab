package app.pacinglab.core.data.repository

import app.pacinglab.core.database.store.EffortStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** How hard the athlete rated workouts (session RPE, 1 to 10), by workout id; used for gym sessions. */
@Singleton
class EffortRepository @Inject constructor(private val store: EffortStore) {
    fun observe(): Flow<Map<String, Int>> = store.observe()

    suspend fun rate(workoutId: String, effort: Int) = store.set(workoutId, effort)

    suspend fun clear(workoutId: String) = store.clear(workoutId)
}
