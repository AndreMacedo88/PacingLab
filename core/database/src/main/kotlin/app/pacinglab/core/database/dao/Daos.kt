package app.pacinglab.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import app.pacinglab.core.database.entity.MeasurementEntity
import app.pacinglab.core.database.entity.PhysiologyEntity
import app.pacinglab.core.database.entity.SleepSessionEntity
import app.pacinglab.core.database.entity.SyncStateEntity
import app.pacinglab.core.database.entity.WorkoutEntity
import app.pacinglab.core.database.entity.WorkoutStreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WorkoutDao {
    @Upsert
    protected abstract suspend fun upsertWorkout(workout: WorkoutEntity)

    @Upsert
    protected abstract suspend fun upsertStreams(streams: List<WorkoutStreamEntity>)

    @Query("DELETE FROM workout_streams WHERE workout_id = :workoutId")
    protected abstract suspend fun deleteStreams(workoutId: String)

    /** Stores a workout and replaces all of its streams atomically. */
    @Transaction
    open suspend fun replace(workout: WorkoutEntity, streams: List<WorkoutStreamEntity>) {
        upsertWorkout(workout)
        deleteStreams(workout.id)
        if (streams.isNotEmpty()) upsertStreams(streams)
    }

    @Query("SELECT * FROM workouts ORDER BY start_ms DESC")
    abstract fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun get(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract fun observe(id: String): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout_streams WHERE workout_id = :workoutId")
    abstract suspend fun streams(workoutId: String): List<WorkoutStreamEntity>

    @Query("SELECT * FROM workouts WHERE end_ms >= :fromMs AND start_ms <= :toMs ORDER BY start_ms")
    abstract suspend fun overlapping(fromMs: Long, toMs: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE source = :source AND source_record_id IN (:recordIds)")
    abstract suspend fun bySourceIds(source: String, recordIds: List<String>): List<WorkoutEntity>

    @Query("DELETE FROM workouts WHERE source = :source AND source_record_id IN (:recordIds)")
    abstract suspend fun deleteBySourceIds(source: String, recordIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM workouts")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT * FROM workouts WHERE route_pending = 1 ORDER BY start_ms")
    abstract suspend fun routePending(): List<WorkoutEntity>
}

@Dao
interface MeasurementDao {
    @Upsert
    suspend fun upsert(measurements: List<MeasurementEntity>)

    @Query("SELECT * FROM measurements WHERE kind = :kind AND time_ms BETWEEN :fromMs AND :toMs ORDER BY time_ms")
    fun observe(kind: String, fromMs: Long, toMs: Long): Flow<List<MeasurementEntity>>

    @Query("DELETE FROM measurements WHERE source_record_id IN (:recordIds)")
    suspend fun delete(recordIds: List<String>): Int
}

@Dao
interface SleepDao {
    @Upsert
    suspend fun upsert(sessions: List<SleepSessionEntity>)

    @Query("SELECT * FROM sleep_sessions WHERE end_ms >= :fromMs AND start_ms <= :toMs ORDER BY start_ms")
    fun observe(fromMs: Long, toMs: Long): Flow<List<SleepSessionEntity>>

    @Query("DELETE FROM sleep_sessions WHERE source_record_id IN (:recordIds)")
    suspend fun delete(recordIds: List<String>): Int
}

@Dao
interface SyncStateDao {
    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM sync_state")
    suspend fun clear()

    @Query("SELECT * FROM sync_state")
    fun observeAll(): Flow<List<SyncStateEntity>>
}

@Dao
abstract class PhysiologyDao {
    @Query("SELECT * FROM physiology ORDER BY effective_from")
    abstract fun observeAll(): Flow<List<PhysiologyEntity>>

    @Upsert
    protected abstract suspend fun upsert(entry: PhysiologyEntity)

    @Query("DELETE FROM physiology WHERE effective_from = :epochDay")
    abstract suspend fun delete(epochDay: Long)

    /** Saves [entry], removing the entry it replaces when its date was changed. */
    @Transaction
    open suspend fun replace(previousEpochDay: Long?, entry: PhysiologyEntity) {
        if (previousEpochDay != null && previousEpochDay != entry.effectiveFromEpochDay) delete(previousEpochDay)
        upsert(entry)
    }
}
