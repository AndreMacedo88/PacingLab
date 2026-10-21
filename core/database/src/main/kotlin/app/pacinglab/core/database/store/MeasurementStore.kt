package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.codec.StreamCodec
import app.pacinglab.core.database.entity.MeasurementEntity
import app.pacinglab.core.database.entity.SleepSessionEntity
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.SleepSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Persists daily measurements (resting HR, HRV, VO2max, ...) and sleep sessions. */
@Singleton
class MeasurementStore @Inject constructor(database: PacingLabDatabase) {
    private val measurements = database.measurementDao()
    private val sleep = database.sleepDao()

    suspend fun saveMeasurements(values: List<Measurement>) {
        if (values.isEmpty()) return
        measurements.upsert(
            values.map {
                MeasurementEntity(
                    sourceRecordId = it.sourceRecordId,
                    kind = it.kind.name,
                    timeMs = it.time.toEpochMilli(),
                    zoneOffsetSeconds = it.zoneOffset?.totalSeconds,
                    value = it.value,
                    origin = it.origin,
                )
            },
        )
    }

    fun observeMeasurements(kind: MeasurementKind, from: Instant, to: Instant): Flow<List<Measurement>> =
        measurements.observe(kind.name, from.toEpochMilli(), to.toEpochMilli()).map { rows ->
            rows.map {
                Measurement(
                    sourceRecordId = it.sourceRecordId,
                    kind = kind,
                    time = Instant.ofEpochMilli(it.timeMs),
                    zoneOffset = it.zoneOffsetSeconds?.let(ZoneOffset::ofTotalSeconds),
                    value = it.value,
                    origin = it.origin,
                )
            }
        }

    suspend fun saveSleep(sessions: List<SleepSession>) {
        if (sessions.isEmpty()) return
        sleep.upsert(
            sessions.map {
                SleepSessionEntity(
                    sourceRecordId = it.sourceRecordId,
                    startMs = it.start.toEpochMilli(),
                    endMs = it.end.toEpochMilli(),
                    zoneOffsetSeconds = it.zoneOffset?.totalSeconds,
                    origin = it.origin,
                    stages = StreamCodec.encodeSleepStages(it.stages),
                    heartRate = it.heartRate.takeUnless { hr -> hr.isEmpty() }?.let(StreamCodec::encodeHeartRate),
                )
            },
        )
    }

    /** Replaces the heart rate stored with a sleep session. */
    suspend fun saveSleepHeartRate(recordId: String, heartRate: HeartRateSeries) {
        sleep.updateHeartRate(recordId, heartRate.takeUnless { it.isEmpty() }?.let(StreamCodec::encodeHeartRate))
    }

    /** Sleep sessions whose time span intersects [from, to], without their heart rate. */
    suspend fun sleepOverlapping(from: Instant, to: Instant): List<SleepSession> =
        sleep.overlapping(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain(withHeartRate = false) }

    fun observeSleep(from: Instant, to: Instant): Flow<List<SleepSession>> =
        sleep.observe(from.toEpochMilli(), to.toEpochMilli()).map { rows -> rows.map { it.toDomain(withHeartRate = true) } }

    private fun SleepSessionEntity.toDomain(withHeartRate: Boolean) = SleepSession(
        sourceRecordId = sourceRecordId,
        start = Instant.ofEpochMilli(startMs),
        end = Instant.ofEpochMilli(endMs),
        zoneOffset = zoneOffsetSeconds?.let(ZoneOffset::ofTotalSeconds),
        origin = origin,
        stages = StreamCodec.decodeSleepStages(stages),
        heartRate = heartRate?.takeIf { withHeartRate }?.let(StreamCodec::decodeHeartRate) ?: HeartRateSeries.EMPTY,
    )

    /** Deletes measurements and sleep sessions with these source ids; unknown ids are ignored. */
    suspend fun delete(recordIds: List<String>) {
        recordIds.chunked(500).forEach {
            measurements.delete(it)
            sleep.delete(it)
        }
    }
}
