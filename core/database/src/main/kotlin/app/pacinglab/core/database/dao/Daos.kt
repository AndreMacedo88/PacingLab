package app.pacinglab.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import app.pacinglab.core.database.entity.DailyVitalsEntity
import app.pacinglab.core.database.entity.EffortEntity
import app.pacinglab.core.database.entity.MeasurementEntity
import app.pacinglab.core.database.entity.PhysiologyEntity
import app.pacinglab.core.database.entity.SessionWeatherEntity
import app.pacinglab.core.database.entity.SleepSessionEntity
import app.pacinglab.core.database.entity.SyncStateEntity
import app.pacinglab.core.database.entity.WellnessEntity
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

    @Query("SELECT * FROM workout_streams WHERE workout_id = :workoutId AND channel = :channel")
    abstract suspend fun stream(workoutId: String, channel: String): WorkoutStreamEntity?

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

    @Query("SELECT * FROM sleep_sessions WHERE end_ms >= :fromMs AND start_ms <= :toMs ORDER BY start_ms")
    suspend fun overlapping(fromMs: Long, toMs: Long): List<SleepSessionEntity>

    @Query("UPDATE sleep_sessions SET heart_rate = :heartRate WHERE source_record_id = :recordId")
    suspend fun updateHeartRate(recordId: String, heartRate: ByteArray?)

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
interface EffortDao {
    @Query("SELECT * FROM efforts")
    fun observeAll(): Flow<List<EffortEntity>>

    @Upsert
    suspend fun upsert(entry: EffortEntity)

    @Query("DELETE FROM efforts WHERE workout_id = :workoutId")
    suspend fun delete(workoutId: String)
}

@Dao
interface SessionWeatherDao {
    @Query("SELECT * FROM session_weather WHERE workout_id = :workoutId")
    fun observe(workoutId: String): Flow<SessionWeatherEntity?>

    @Query("SELECT * FROM session_weather WHERE workout_id = :workoutId")
    suspend fun get(workoutId: String): SessionWeatherEntity?

    @Query("SELECT * FROM session_weather")
    fun observeAll(): Flow<List<SessionWeatherEntity>>

    @Query("SELECT workout_id FROM session_weather")
    suspend fun ids(): List<String>

    @Upsert
    suspend fun upsert(entry: SessionWeatherEntity)

    @Query("DELETE FROM session_weather")
    suspend fun clear()
}

@Dao
interface WellnessDao {
    @Query("SELECT * FROM wellness ORDER BY epoch_day")
    fun observeAll(): Flow<List<WellnessEntity>>

    @Upsert
    suspend fun upsert(entry: WellnessEntity)

    @Query("DELETE FROM wellness WHERE epoch_day = :epochDay")
    suspend fun delete(epochDay: Long)
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

@Dao
abstract class DailyVitalsDao {
    @Query("SELECT * FROM daily_vitals ORDER BY epoch_day")
    abstract fun observeAll(): Flow<List<DailyVitalsEntity>>

    @Query("SELECT * FROM daily_vitals WHERE epoch_day IN (:epochDays)")
    abstract suspend fun get(epochDays: List<Long>): List<DailyVitalsEntity>

    @Query("SELECT MAX(epoch_day) FROM daily_vitals")
    abstract suspend fun latest(): Long?

    @Query("SELECT MAX(epoch_day) FROM daily_vitals WHERE vo2max IS NOT NULL")
    abstract suspend fun latestVo2Max(): Long?

    @Upsert
    protected abstract suspend fun upsert(entries: List<DailyVitalsEntity>)

    @Query("DELETE FROM daily_vitals")
    abstract suspend fun clear()

    /** Saves [entries], keeping stored values that an entry leaves empty. */
    @Transaction
    open suspend fun merge(
        entries: List<DailyVitalsEntity>,
        combine: (new: DailyVitalsEntity, old: DailyVitalsEntity) -> DailyVitalsEntity,
    ) {
        val stored = get(entries.map { it.epochDay }).associateBy { it.epochDay }
        upsert(entries.map { entry -> stored[entry.epochDay]?.let { combine(entry, it) } ?: entry })
    }
}
