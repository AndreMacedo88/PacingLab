package app.pacinglab.core.database.store

import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.codec.StreamCodec
import app.pacinglab.core.database.entity.WorkoutEntity
import app.pacinglab.core.database.entity.WorkoutStreamEntity
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Persists workouts and their streams. */
@Singleton
class WorkoutStore @Inject constructor(database: PacingLabDatabase) {
    private val dao = database.workoutDao()

    suspend fun save(workout: Workout, streams: WorkoutStreams) {
        val encoded = buildList {
            if (!streams.heartRate.isEmpty()) {
                add(WorkoutStreamEntity(workout.id, HEART_RATE, streams.heartRate.size, StreamCodec.encodeHeartRate(streams.heartRate)))
            }
            if (!streams.speed.isEmpty()) {
                add(
                    WorkoutStreamEntity(
                        workout.id,
                        SPEED,
                        streams.speed.size,
                        StreamCodec.encodeSamples(streams.speed, StreamCodec.SPEED_SCALE),
                    ),
                )
            }
            if (!streams.route.isEmpty()) {
                add(WorkoutStreamEntity(workout.id, ROUTE, streams.route.size, StreamCodec.encodeRoute(streams.route)))
            }
        }
        dao.replace(workout.toEntity(), encoded)
    }

    fun observeAll(): Flow<List<Workout>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observe(id: String): Flow<Workout?> = dao.observe(id).map { it?.toDomain() }

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun get(id: String): Workout? = dao.get(id)?.toDomain()

    suspend fun streams(id: String, reportedDistanceMeters: Double? = null): WorkoutStreams {
        val byChannel = dao.streams(id).associateBy { it.channel }
        return WorkoutStreams(
            heartRate = byChannel[HEART_RATE]?.let { StreamCodec.decodeHeartRate(it.data) } ?: HeartRateSeries.EMPTY,
            speed = byChannel[SPEED]?.let { StreamCodec.decodeSamples(it.data, StreamCodec.SPEED_SCALE) } ?: SampleSeries.EMPTY,
            route = byChannel[ROUTE]?.let { StreamCodec.decodeRoute(it.data) } ?: Route.EMPTY,
            reportedDistanceMeters = reportedDistanceMeters,
        )
    }

    /** Only the heart rate of a workout, for calculations over the whole history. */
    suspend fun heartRate(id: String): HeartRateSeries =
        dao.stream(id, HEART_RATE)?.let { StreamCodec.decodeHeartRate(it.data) } ?: HeartRateSeries.EMPTY

    /** Workouts whose time span intersects [from, to]. */
    suspend fun overlapping(from: Instant, to: Instant): List<Workout> =
        dao.overlapping(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }

    /** Workouts whose route could not be read yet, oldest first. */
    suspend fun routePending(): List<Workout> = dao.routePending().map { it.toDomain() }

    suspend fun bySourceIds(source: DataSource, recordIds: List<String>): List<Workout> =
        recordIds.chunked(SQL_VARIABLE_CHUNK).flatMap { chunk -> dao.bySourceIds(source.name, chunk).map { it.toDomain() } }

    suspend fun deleteBySourceIds(source: DataSource, recordIds: List<String>): Int =
        recordIds.chunked(SQL_VARIABLE_CHUNK).sumOf { dao.deleteBySourceIds(source.name, it) }

    private fun Workout.toEntity() = WorkoutEntity(
        id = id,
        source = source.name,
        sourceRecordId = sourceRecordId,
        origin = origin,
        device = device,
        activity = activity,
        title = title,
        startMs = start.toEpochMilli(),
        endMs = end.toEpochMilli(),
        zoneOffsetSeconds = zoneOffset?.totalSeconds,
        lastModifiedMs = lastModified.toEpochMilli(),
        sport = sport.name,
        durationMs = summary.durationMillis,
        distanceMeters = summary.distanceMeters,
        averageHr = summary.averageHr,
        maxHr = summary.maxHr,
        hasRoute = summary.hasRoute,
        hasSpeed = summary.hasSpeed,
        laps = StreamCodec.encodeLaps(laps),
        segments = StreamCodec.encodeSegments(segments),
        routePending = routePending,
        recordingMethod = recordingMethod.name,
    )

    private fun WorkoutEntity.toDomain() = Workout(
        id = id,
        source = DataSource.valueOf(source),
        sourceRecordId = sourceRecordId,
        origin = origin,
        device = device,
        activity = activity,
        title = title,
        start = Instant.ofEpochMilli(startMs),
        end = Instant.ofEpochMilli(endMs),
        zoneOffset = zoneOffsetSeconds?.let(ZoneOffset::ofTotalSeconds),
        lastModified = Instant.ofEpochMilli(lastModifiedMs),
        sport = Sport.entries.firstOrNull { it.name == sport } ?: Sport.OTHER,
        summary = WorkoutSummary(
            durationMillis = durationMs,
            distanceMeters = distanceMeters,
            averageHr = averageHr,
            maxHr = maxHr,
            hasRoute = hasRoute,
            hasSpeed = hasSpeed,
        ),
        laps = StreamCodec.decodeLaps(laps),
        segments = StreamCodec.decodeSegments(segments),
        routePending = routePending,
        recordingMethod = RecordingMethod.entries.firstOrNull { it.name == recordingMethod } ?: RecordingMethod.UNKNOWN,
    )

    private companion object {
        const val HEART_RATE = "heart_rate"
        const val SPEED = "speed"
        const val ROUTE = "route"

        /** SQLite limits bound parameters per statement; stay well below the default of 999. */
        const val SQL_VARIABLE_CHUNK = 500
    }
}
