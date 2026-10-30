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
    @ColumnInfo(name = "heart_rate", typeAffinity = ColumnInfo.BLOB) val heartRate: ByteArray? = null,
)

/** Small key-value store for sync bookkeeping (change tokens, backfill progress). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val key: String, val value: String)

/**
 * How the athlete felt on a day, as they rated it (stored by epoch day). [rested] answers "how rested do
 * you feel?" on a scale of 1 (exhausted) to 5 (fully rested).
 */
@Entity(tableName = "wellness")
class WellnessEntity(@PrimaryKey @ColumnInfo(name = "epoch_day") val epochDay: Long, @ColumnInfo(name = "rested") val rested: Int?)

/** How hard the athlete rated a workout, 1 to 10 (session RPE). */
@Entity(tableName = "efforts")
class EffortEntity(@PrimaryKey @ColumnInfo(name = "workout_id") val workoutId: String, @ColumnInfo(name = "effort") val effort: Int)

/** Heart rate values the athlete set, valid from a date (stored as epoch day) until the next entry. */
@Entity(tableName = "physiology")
class PhysiologyEntity(
    @PrimaryKey @ColumnInfo(name = "effective_from") val effectiveFromEpochDay: Long,
    @ColumnInfo(name = "max_hr") val maxHr: Int?,
    @ColumnInfo(name = "threshold_hr") val thresholdHr: Int?,
    @ColumnInfo(name = "resting_hr") val restingHr: Int?,
)

/** The watch app's nightly values from a secondary source, by the date of the morning (epoch day). */
@Entity(tableName = "daily_vitals")
class DailyVitalsEntity(
    @PrimaryKey @ColumnInfo(name = "epoch_day") val epochDay: Long,
    @ColumnInfo(name = "hrv_ms") val hrvMs: Double?,
    @ColumnInfo(name = "resting_hr") val restingHr: Double?,
    @ColumnInfo(name = "breathing_rate") val breathingRate: Double?,
    @ColumnInfo(name = "spo2") val oxygenSaturation: Double?,
    @ColumnInfo(name = "spo2_low") val oxygenSaturationLow: Double?,
    @ColumnInfo(name = "spo2_high") val oxygenSaturationHigh: Double?,
    @ColumnInfo(name = "skin_temperature") val skinTemperature: Double?,
    @ColumnInfo(name = "skin_temperature_baseline") val skinTemperatureBaseline: Double?,
    @ColumnInfo(name = "vo2max") val vo2max: Double? = null,
    @ColumnInfo(name = "vo2max_variance") val vo2maxVariance: Double? = null,
    /** Brief awakenings as "start-end" epoch seconds joined by commas; empty for none, null when unknown. */
    @ColumnInfo(name = "brief_awakenings") val briefAwakenings: String? = null,
)

/**
 * The weather during a session, looked up once by its first workout's id. A row without a temperature
 * records that none could be found, so the lookup is not repeated.
 */
@Entity(tableName = "session_weather")
class SessionWeatherEntity(
    @PrimaryKey @ColumnInfo(name = "workout_id") val workoutId: String,
    @ColumnInfo(name = "fetched_ms") val fetchedMs: Long,
    @ColumnInfo(name = "time_ms") val timeMs: Long,
    @ColumnInfo(name = "condition") val condition: String?,
    @ColumnInfo(name = "temperature") val temperature: Double?,
    @ColumnInfo(name = "apparent_temperature") val apparentTemperature: Double?,
    @ColumnInfo(name = "dew_point") val dewPoint: Double?,
    @ColumnInfo(name = "humidity") val humidity: Double?,
    @ColumnInfo(name = "wind_speed") val windSpeed: Double?,
    @ColumnInfo(name = "wind_gusts") val windGusts: Double?,
    @ColumnInfo(name = "precipitation") val precipitation: Double?,
)
