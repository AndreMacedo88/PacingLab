package app.pacinglab.core.healthconnect.probe

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import app.pacinglab.core.healthconnect.HealthConnectManager
import app.pacinglab.core.healthconnect.HealthConnectPermissions
import app.pacinglab.core.healthconnect.exerciseTypeName
import app.pacinglab.core.healthconnect.readAll
import app.pacinglab.core.healthconnect.readEdge
import app.pacinglab.core.healthconnect.timingOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.reflect.KClass

/** Progress of a running probe, for display. */
data class ProbeProgress(val stage: Stage, val current: Int, val total: Int, val recordType: String? = null) {
    enum class Stage { PERMISSIONS, RECORD_TYPES, SESSIONS, DAILY_VALUES, DONE }
}

/**
 * Measures what Health Connect actually holds: which record types exist, how far back they go,
 * which apps wrote them, how densely streams are sampled during workouts, and whether routes,
 * laps and segments are present.
 *
 * The answers decide how PacingLab ingests data (see PLAN.md, milestone M1).
 */
class HealthDataProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val manager: HealthConnectManager,
    private val clock: Clock,
) {
    private val client: HealthConnectClient get() = manager.client

    suspend fun run(onProgress: (ProbeProgress) -> Unit = {}): ProbeReport = withContext(Dispatchers.Default) {
        val startedAt = clock.millis()
        val now = clock.instant()
        val errors = mutableListOf<String>()

        onProgress(ProbeProgress(ProbeProgress.Stage.PERMISSIONS, 0, 1))
        val granted = manager.grantedPermissions()
        val features = manager.featureSupport()

        val types = HealthConnectPermissions.recordTypes
        val recordTypes = types.mapIndexed { index, type ->
            onProgress(ProbeProgress(ProbeProgress.Stage.RECORD_TYPES, index + 1, types.size, type.simpleName))
            summarizeType(type, granted, now)
        }

        val sessionTypeCounts = mutableMapOf<String, Int>()
        val sessions = mutableListOf<SessionSummary>()
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            attempt(errors, "sessions") {
                val recent = mutableListOf<ExerciseSessionRecord>()
                client.readAll(ExerciseSessionRecord::class, TimeRangeFilter.before(now), ascending = false) { page ->
                    page.forEach { session ->
                        sessionTypeCounts.increment(exerciseTypeName(session.exerciseType))
                        if (recent.size < RECENT_SESSIONS) recent += session
                    }
                }
                recent.forEachIndexed { index, session ->
                    onProgress(ProbeProgress(ProbeProgress.Stage.SESSIONS, index + 1, recent.size))
                    sessions += summarizeSession(session, granted)
                }
            }
        }

        onProgress(ProbeProgress(ProbeProgress.Stage.DAILY_VALUES, 0, 1))
        val allDayHeartRate = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            attempt(errors, "all-day heart rate") {
                seriesSummary(HeartRateRecord::class, now.minus(ALL_DAY_WINDOW), now, sessionBounds = null) { record ->
                    record.samples.map { it.time to it.beatsPerMinute.toDouble() }
                }
            }
        } else {
            null
        }
        val recentDailyValues = recentDailyValues(granted, now, errors)
        val recentSleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            attempt(errors, "sleep") { recentSleep(now) }.orEmpty()
        } else {
            emptyList()
        }

        onProgress(ProbeProgress(ProbeProgress.Stage.DONE, 1, 1))
        ProbeReport(
            generatedAt = now.toString(),
            probeDurationMs = clock.millis() - startedAt,
            appVersion = appVersion(),
            device = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, Build.VERSION.RELEASE),
            healthConnect = HealthConnectInfo(
                availability = manager.availability().name,
                historyFeature = features.readHistory,
                backgroundFeature = features.readInBackground,
                granted = granted.sorted(),
                missing = (HealthConnectPermissions.all - granted).sorted(),
            ),
            recordTypes = recordTypes,
            sessionTypeCounts = sessionTypeCounts.toSortedMap(),
            sessions = sessions,
            allDayHeartRate = allDayHeartRate,
            recentDailyValues = recentDailyValues,
            recentSleep = recentSleep,
            errors = errors,
        )
    }

    private suspend fun <T : Record> summarizeType(type: KClass<T>, granted: Set<String>, now: Instant): RecordTypeSummary {
        val name = type.simpleName.orEmpty().removeSuffix("Record")
        if (HealthPermission.getReadPermission(type) !in granted) return RecordTypeSummary(type = name, granted = false)
        return try {
            val everything = TimeRangeFilter.before(now)
            val earliest = client.readEdge(type, everything, ascending = true)?.let { timingOf(it).start }
            val latest = client.readEdge(type, everything, ascending = false)?.let { timingOf(it).start }
            var records = 0
            var samples: Int? = null
            val spans = mutableListOf<Double>()
            val origins = mutableMapOf<String, Int>()
            val devices = mutableMapOf<String, Int>()
            val methods = mutableMapOf<String, Int>()
            client.readAll(type, TimeRangeFilter.between(now.minus(TYPE_WINDOW), now)) { page ->
                for (record in page) {
                    records++
                    val timing = timingOf(record)
                    timing.samples?.let { samples = (samples ?: 0) + it }
                    timing.end?.let { spans += Duration.between(timing.start, it).toMillis() / 1000.0 }
                    origins.increment(record.metadata.dataOrigin.packageName)
                    devices.increment(deviceLabel(record.metadata.device))
                    methods.increment(recordingMethodName(record.metadata.recordingMethod))
                }
            }
            RecordTypeSummary(
                type = name,
                granted = true,
                earliest = earliest?.toString(),
                latest = latest?.toString(),
                recordsLast30Days = records,
                samplesLast30Days = samples,
                medianRecordSpanS = spans.median(),
                origins = origins.toSortedMap(),
                devices = devices.toSortedMap(),
                recordingMethods = methods.toSortedMap(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecordTypeSummary(type = name, granted = true, error = e.describe())
        }
    }

    private suspend fun summarizeSession(listed: ExerciseSessionRecord, granted: Set<String>): SessionSummary {
        val errors = mutableListOf<String>()
        // Routes are only attached when a session is read on its own.
        val session = attempt(errors, "read session") {
            client.readRecord(ExerciseSessionRecord::class, listed.metadata.id).record
        } ?: listed
        val start = session.startTime
        val end = session.endTime
        val bounds = start to end

        fun has(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

        return SessionSummary(
            id = session.metadata.id
                .hashCode()
                .toUInt()
                .toString(16),
            start = start.toString(),
            end = end.toString(),
            zoneOffset = session.startZoneOffset?.toString(),
            durationS = Duration.between(start, end).seconds,
            exerciseType = session.exerciseType,
            exerciseTypeName = exerciseTypeName(session.exerciseType),
            title = session.title,
            origin = session.metadata.dataOrigin.packageName,
            device = deviceLabel(session.metadata.device),
            recordingMethod = recordingMethodName(session.metadata.recordingMethod),
            laps = session.laps.size,
            segmentTypes = session.segments
                .groupingBy { it.segmentType }
                .eachCount()
                .toSortedMap(),
            route = routeSummary(session.exerciseRouteResult),
            bulkRouteStatus = routeSummary(listed.exerciseRouteResult).status,
            heartRate = if (has(HeartRateRecord::class)) {
                attempt(errors, "heart rate") {
                    seriesSummary(HeartRateRecord::class, start, end, bounds) { r ->
                        r.samples.map { it.time to it.beatsPerMinute.toDouble() }
                    }
                }
            } else {
                null
            },
            speed = if (has(SpeedRecord::class)) {
                attempt(errors, "speed") {
                    seriesSummary(SpeedRecord::class, start, end, bounds) { r ->
                        r.samples.map { it.time to it.speed.inMetersPerSecond }
                    }
                }
            } else {
                null
            },
            stepsCadence = if (has(StepsCadenceRecord::class)) {
                attempt(errors, "cadence") {
                    seriesSummary(StepsCadenceRecord::class, start, end, bounds) { r ->
                        r.samples.map { it.time to it.rate }
                    }
                }
            } else {
                null
            },
            power = if (has(PowerRecord::class)) {
                attempt(errors, "power") {
                    seriesSummary(PowerRecord::class, start, end, bounds) { r ->
                        r.samples.map { it.time to it.power.inWatts }
                    }
                }
            } else {
                null
            },
            distance = if (has(DistanceRecord::class)) {
                attempt(errors, "distance") { intervalSummary(DistanceRecord::class, start, end) { it.distance.inMeters } }
            } else {
                null
            },
            elevationGained = if (has(ElevationGainedRecord::class)) {
                attempt(errors, "elevation") {
                    intervalSummary(ElevationGainedRecord::class, start, end) { it.elevation.inMeters }
                }
            } else {
                null
            },
            steps = if (has(StepsRecord::class)) {
                attempt(errors, "steps") { intervalSummary(StepsRecord::class, start, end) { it.count.toDouble() } }
            } else {
                null
            },
            activeCalories = if (has(ActiveCaloriesBurnedRecord::class)) {
                attempt(errors, "active calories") {
                    intervalSummary(ActiveCaloriesBurnedRecord::class, start, end) { it.energy.inKilocalories }
                }
            } else {
                null
            },
            errors = errors,
        )
    }

    private fun routeSummary(result: ExerciseRouteResult): RouteSummary = when (result) {
        is ExerciseRouteResult.Data -> summarizeRoute(result.exerciseRoute)
        is ExerciseRouteResult.ConsentRequired -> RouteSummary(status = "consent_required")
        is ExerciseRouteResult.NoData -> RouteSummary(status = "no_data")
        else -> RouteSummary(status = "unknown")
    }

    private fun summarizeRoute(route: ExerciseRoute): RouteSummary {
        val points = route.route
        var length = 0.0
        for (i in 1 until points.size) {
            length +=
                haversineMeters(
                    points[i - 1].latitude,
                    points[i - 1].longitude,
                    points[i].latitude,
                    points[i].longitude,
                )
        }
        return RouteSummary(
            status = "data",
            points = points.size,
            spacing = SpacingStats.of(LongArray(points.size) { points[it].time.toEpochMilli() }),
            pointsWithAltitude = points.count { it.altitude != null },
            pointsWithHorizontalAccuracy = points.count { it.horizontalAccuracy != null },
            medianHorizontalAccuracyM = points.mapNotNull { it.horizontalAccuracy?.inMeters }.median(),
            pathLengthM = length,
        )
    }

    /**
     * Summarises the samples of a series type between [from] and [to]. Records overlapping the
     * window can carry samples outside it, so samples are clipped to the window.
     */
    private suspend fun <T : Record> seriesSummary(
        type: KClass<T>,
        from: Instant,
        to: Instant,
        sessionBounds: Pair<Instant, Instant>?,
        samplesOf: (T) -> List<Pair<Instant, Double>>,
    ): SeriesSummary? {
        var records = 0
        val samples = mutableListOf<Pair<Instant, Double>>()
        client.readAll(type, TimeRangeFilter.between(from, to)) { page ->
            records += page.size
            page.forEach { record -> samplesOf(record).filterTo(samples) { (time, _) -> time in from..to } }
        }
        if (records == 0) return null
        samples.sortBy { it.first }
        val values = samples.map { it.second }
        return SeriesSummary(
            records = records,
            spacing = SpacingStats.of(LongArray(samples.size) { samples[it].first.toEpochMilli() }),
            min = values.minOrNull(),
            max = values.maxOrNull(),
            mean = if (values.isEmpty()) null else values.average(),
            firstOffsetS = sessionBounds?.let { (start, _) ->
                samples.firstOrNull()?.let { Duration.between(start, it.first).seconds }
            },
            lastOffsetS = sessionBounds?.let { (_, end) ->
                samples.lastOrNull()?.let { Duration.between(it.first, end).seconds }
            },
        )
    }

    private suspend fun <T : Record> intervalSummary(
        type: KClass<T>,
        from: Instant,
        to: Instant,
        valueOf: (T) -> Double,
    ): IntervalSummary? {
        var total = 0.0
        val spans = mutableListOf<Double>()
        client.readAll(type, TimeRangeFilter.between(from, to)) { page ->
            page.forEach {
                val timing = timingOf(it)
                total += valueOf(it)
                spans += Duration.between(timing.start, timing.end ?: timing.start).toMillis() / 1000.0
            }
        }
        return if (spans.isEmpty()) null else IntervalSummary(spans.size, total, spans.median())
    }

    private suspend fun recentDailyValues(granted: Set<String>, now: Instant, errors: MutableList<String>): Map<String, List<TimedValue>> {
        val window = TimeRangeFilter.between(now.minus(DAILY_WINDOW), now)
        val result = sortedMapOf<String, List<TimedValue>>()

        suspend fun <T : Record> collect(type: KClass<T>, toValue: (T) -> TimedValue) {
            if (HealthPermission.getReadPermission(type) !in granted) return
            val name = type.simpleName.orEmpty().removeSuffix("Record")
            attempt(errors, name) {
                val values = mutableListOf<TimedValue>()
                client.readAll(type, window, ascending = false) { page ->
                    page.forEach { if (values.size < MAX_DAILY_VALUES) values += toValue(it) }
                }
                result[name] = values
            }
        }

        collect(RestingHeartRateRecord::class) { TimedValue(it.time.toString(), it.beatsPerMinute.toDouble()) }
        collect(HeartRateVariabilityRmssdRecord::class) {
            TimedValue(it.time.toString(), it.heartRateVariabilityMillis)
        }
        collect(Vo2MaxRecord::class) {
            TimedValue(it.time.toString(), it.vo2MillilitersPerMinuteKilogram, "method ${it.measurementMethod}")
        }
        collect(RespiratoryRateRecord::class) { TimedValue(it.time.toString(), it.rate) }
        collect(OxygenSaturationRecord::class) { TimedValue(it.time.toString(), it.percentage.value) }
        collect(WeightRecord::class) { TimedValue(it.time.toString(), it.weight.inKilograms) }
        collect(SkinTemperatureRecord::class) {
            TimedValue(
                time = it.startTime.toString(),
                value = it.baseline?.inCelsius,
                note = "${it.deltas.size} deltas, ${Duration.between(it.startTime, it.endTime).toMinutes()} min",
            )
        }
        return result
    }

    private suspend fun recentSleep(now: Instant): List<SleepSummary> {
        val sleeps = mutableListOf<SleepSummary>()
        client.readAll(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(DAILY_WINDOW), now), ascending = false) {
            it.forEach { sleep ->
                if (sleeps.size < MAX_SLEEPS) {
                    sleeps +=
                        SleepSummary(
                            start = sleep.startTime.toString(),
                            end = sleep.endTime.toString(),
                            stages = sleep.stages
                                .groupingBy { stage -> stage.stage }
                                .eachCount()
                                .toSortedMap(),
                            origin = sleep.metadata.dataOrigin.packageName,
                        )
                }
            }
        }
        return sleeps
    }

    private fun appVersion(): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"

    private inline fun <T> attempt(errors: MutableList<String>, label: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += "$label: ${e.describe()}"
        null
    }

    companion object {
        private const val RECENT_SESSIONS = 15
        private const val MAX_DAILY_VALUES = 60
        private const val MAX_SLEEPS = 7
        private val TYPE_WINDOW: Duration = Duration.ofDays(30)
        private val ALL_DAY_WINDOW: Duration = Duration.ofHours(48)
        private val DAILY_WINDOW: Duration = Duration.ofDays(14)
    }
}

private fun deviceLabel(device: Device?): String =
    if (device == null) "none" else listOfNotNull(device.manufacturer, device.model).joinToString(" ").ifBlank { "type ${device.type}" }

private fun recordingMethodName(method: Int): String = when (method) {
    Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> "active"
    Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> "automatic"
    Metadata.RECORDING_METHOD_MANUAL_ENTRY -> "manual"
    else -> "unknown"
}

private fun Exception.describe(): String = "${this::class.simpleName}: ${message.orEmpty()}"

private fun MutableMap<String, Int>.increment(key: String) {
    this[key] = (this[key] ?: 0) + 1
}

private fun List<Double>.median(): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
