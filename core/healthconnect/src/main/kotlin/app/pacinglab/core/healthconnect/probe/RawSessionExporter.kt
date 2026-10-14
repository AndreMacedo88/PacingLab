package app.pacinglab.core.healthconnect.probe

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.time.TimeRangeFilter
import app.pacinglab.core.healthconnect.HealthConnectManager
import app.pacinglab.core.healthconnect.exerciseTypeName
import app.pacinglab.core.healthconnect.readAll
import app.pacinglab.core.healthconnect.timingOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Full-resolution dump of recent sessions. Contains GPS coordinates, so it must never be
 * committed: it is meant for the git-ignored fixtures-private/ folder only.
 * Times are epoch milliseconds.
 */
@Serializable
data class RawSessionsExport(val formatVersion: Int = 1, val generatedAt: String, val sessions: List<RawSession>)

@Serializable
data class RawSession(
    val id: String,
    val start: Long,
    val end: Long,
    val zoneOffset: String?,
    val exerciseType: Int,
    val exerciseTypeName: String,
    val title: String?,
    val origin: String,
    val laps: List<RawInterval>,
    val segments: List<RawSegment>,
    val routeStatus: String,
    val route: List<RawLocation>,
    val heartRate: List<RawSample>,
    val speed: List<RawSample>,
    val cadence: List<RawSample>,
    val power: List<RawSample>,
    val distance: List<RawInterval>,
    val elevationGained: List<RawInterval>,
    val steps: List<RawInterval>,
    val activeCalories: List<RawInterval>,
    val errors: List<String>,
)

@Serializable
data class RawSample(val t: Long, val v: Double)

@Serializable
data class RawInterval(val start: Long, val end: Long, val v: Double?)

@Serializable
data class RawSegment(val start: Long, val end: Long, val type: Int, val repetitions: Int)

@Serializable
data class RawLocation(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double? = null,
    val hAcc: Double? = null,
    val vAcc: Double? = null,
)

class RawSessionExporter @Inject constructor(private val manager: HealthConnectManager, private val clock: Clock) {
    private val client: HealthConnectClient get() = manager.client

    suspend fun export(sessionCount: Int = DEFAULT_SESSIONS): RawSessionsExport = withContext(Dispatchers.Default) {
        val now = clock.instant()
        val listed = mutableListOf<ExerciseSessionRecord>()
        client.readAll(ExerciseSessionRecord::class, TimeRangeFilter.before(now), ascending = false) { page ->
            page.forEach { if (listed.size < sessionCount) listed += it }
        }
        RawSessionsExport(generatedAt = now.toString(), sessions = listed.map { exportSession(it) })
    }

    private suspend fun exportSession(listed: ExerciseSessionRecord): RawSession {
        val errors = mutableListOf<String>()
        val session = attempt(errors, "read session") {
            client.readRecord(ExerciseSessionRecord::class, listed.metadata.id).record
        } ?: listed
        val from = session.startTime
        val to = session.endTime
        val route = session.exerciseRouteResult
        return RawSession(
            id = session.metadata.id
                .hashCode()
                .toUInt()
                .toString(16),
            start = from.toEpochMilli(),
            end = to.toEpochMilli(),
            zoneOffset = session.startZoneOffset?.toString(),
            exerciseType = session.exerciseType,
            exerciseTypeName = exerciseTypeName(session.exerciseType),
            title = session.title,
            origin = session.metadata.dataOrigin.packageName,
            laps = session.laps.map { RawInterval(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.length?.inMeters) },
            segments = session.segments.map {
                RawSegment(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.segmentType, it.repetitions)
            },
            routeStatus = when (route) {
                is ExerciseRouteResult.Data -> "data"
                is ExerciseRouteResult.ConsentRequired -> "consent_required"
                is ExerciseRouteResult.NoData -> "no_data"
                else -> "unknown"
            },
            route = (route as? ExerciseRouteResult.Data)?.exerciseRoute?.route.orEmpty().map {
                RawLocation(
                    t = it.time.toEpochMilli(),
                    lat = it.latitude,
                    lon = it.longitude,
                    alt = it.altitude?.inMeters,
                    hAcc = it.horizontalAccuracy?.inMeters,
                    vAcc = it.verticalAccuracy?.inMeters,
                )
            },
            heartRate = attempt(errors, "heart rate") {
                samples(HeartRateRecord::class, from, to) { r -> r.samples.map { it.time to it.beatsPerMinute.toDouble() } }
            }.orEmpty(),
            speed = attempt(errors, "speed") {
                samples(SpeedRecord::class, from, to) { r -> r.samples.map { it.time to it.speed.inMetersPerSecond } }
            }.orEmpty(),
            cadence = attempt(errors, "cadence") {
                samples(StepsCadenceRecord::class, from, to) { r -> r.samples.map { it.time to it.rate } }
            }.orEmpty(),
            power = attempt(errors, "power") {
                samples(PowerRecord::class, from, to) { r -> r.samples.map { it.time to it.power.inWatts } }
            }.orEmpty(),
            distance = attempt(errors, "distance") {
                intervals(DistanceRecord::class, from, to) { it.distance.inMeters }
            }.orEmpty(),
            elevationGained = attempt(errors, "elevation") {
                intervals(ElevationGainedRecord::class, from, to) { it.elevation.inMeters }
            }.orEmpty(),
            steps = attempt(
                errors,
                "steps",
            ) { intervals(StepsRecord::class, from, to) { it.count.toDouble() } }.orEmpty(),
            activeCalories = attempt(errors, "active calories") {
                intervals(ActiveCaloriesBurnedRecord::class, from, to) { it.energy.inKilocalories }
            }.orEmpty(),
            errors = errors,
        )
    }

    private suspend fun <T : Record> samples(
        type: KClass<T>,
        from: Instant,
        to: Instant,
        samplesOf: (T) -> List<Pair<Instant, Double>>,
    ): List<RawSample> {
        val result = mutableListOf<RawSample>()
        client.readAll(type, TimeRangeFilter.between(from, to)) { page ->
            page.forEach { record ->
                samplesOf(record).forEach { (time, value) ->
                    if (time in from..to) result += RawSample(time.toEpochMilli(), value)
                }
            }
        }
        return result.sortedBy { it.t }
    }

    private suspend fun <T : Record> intervals(type: KClass<T>, from: Instant, to: Instant, valueOf: (T) -> Double): List<RawInterval> {
        val result = mutableListOf<RawInterval>()
        client.readAll(type, TimeRangeFilter.between(from, to)) { page ->
            page.forEach {
                val timing = timingOf(it)
                result += RawInterval(timing.start.toEpochMilli(), (timing.end ?: timing.start).toEpochMilli(), valueOf(it))
            }
        }
        return result.sortedBy { it.start }
    }

    private inline fun <T> attempt(errors: MutableList<String>, label: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += "$label: ${e::class.simpleName}: ${e.message.orEmpty()}"
        null
    }

    companion object {
        const val DEFAULT_SESSIONS = 8
    }
}
