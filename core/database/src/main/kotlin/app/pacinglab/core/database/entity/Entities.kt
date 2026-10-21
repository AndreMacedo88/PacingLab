package app.pacinglab.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "workouts", indices = [Index("start_ms"), Index("source_record_id")])
class WorkoutEntity(
    @PrimaryKey val id: String,
    val source: String,
    @ColumnInfo(name = "source_record_id") val sourceRecordId: String,
    val origin: String,
    val device: String?,
    val activity: String,
    val title: String?,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "zone_offset_s") val zoneOffsetSeconds: Int?,
    @ColumnInfo(name = "last_modified_ms") val lastModifiedMs: Long,
    val sport: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "distance_m") val distanceMeters: Double?,
    @ColumnInfo(name = "avg_hr") val averageHr: Int?,
    @ColumnInfo(name = "max_hr") val maxHr: Int?,
    @ColumnInfo(name = "has_route") val hasRoute: Boolean,
    @ColumnInfo(name = "has_speed") val hasSpeed: Boolean,
    @ColumnInfo(name = "laps", typeAffinity = ColumnInfo.BLOB) val laps: ByteArray,
    @ColumnInfo(name = "segments", typeAffinity = ColumnInfo.BLOB) val segments: ByteArray,
    @ColumnInfo(name = "route_pending", defaultValue = "0") val routePending: Boolean,
    @ColumnInfo(name = "recording_method", defaultValue = "UNKNOWN") val recordingMethod: String,
)

/** One encoded stream (see StreamCodec) per workout and channel. */
@Entity(
    tableName = "workout_streams",
    primaryKeys = ["workout_id", "channel"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class WorkoutStreamEntity(
    @ColumnInfo(name = "workout_id") val workoutId: String,
    val channel: String,
    val samples: Int,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val data: ByteArray,
)

@Entity(tableName = "measurements", indices = [Index(value = ["kind", "time_ms"])])
data class MeasurementEntity(
    @PrimaryKey @ColumnInfo(name = "source_record_id") val sourceRecordId: String,
    val kind: String,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    @ColumnInfo(name = "zone_offset_s") val zoneOffsetSeconds: Int?,
    val value: Double,
    val origin: String,
)

@Entity(tableName = "sleep_sessions", indices = [Index("start_ms")])
class SleepSessionEntity(
    @PrimaryKey @ColumnInfo(name = "source_record_id") val sourceRecordId: String,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "zone_offset_s") val zoneOffsetSeconds: Int?,
    val origin: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val stages: ByteArray,
)

/** Small key-value store for sync bookkeeping (change tokens, backfill progress). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val key: String, val value: String)

/** Heart rate values the athlete set, valid from a date (stored as epoch day) until the next entry. */
@Entity(tableName = "physiology")
class PhysiologyEntity(
    @PrimaryKey @ColumnInfo(name = "effective_from") val effectiveFromEpochDay: Long,
    @ColumnInfo(name = "max_hr") val maxHr: Int?,
    @ColumnInfo(name = "threshold_hr") val thresholdHr: Int?,
    @ColumnInfo(name = "resting_hr") val restingHr: Int?,
)
