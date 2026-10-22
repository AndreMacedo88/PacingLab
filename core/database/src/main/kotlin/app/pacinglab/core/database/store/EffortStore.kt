package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.entity.EffortEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists how hard the athlete rated workouts, 1 to 10, by workout id. */
@Singleton
class EffortStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.effortDao()

    fun observe(): Flow<Map<String, Int>> = dao.observeAll().map { rows -> rows.associate { it.workoutId to it.effort } }

    suspend fun set(workoutId: String, effort: Int) {
        require(effort in RANGE) { "Effort $effort outside $RANGE" }
        dao.upsert(EffortEntity(workoutId, effort))
    }

    suspend fun clear(workoutId: String) = dao.delete(workoutId)

    companion object {
        val RANGE = 1..10
    }
}
