package app.pacinglab.core.healthconnect.source

import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import app.pacinglab.core.healthconnect.exerciseTypeName
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Lap
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Segment
import app.pacinglab.core.model.SegmentKind
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.SourceWorkout
import java.time.Instant

internal fun ExerciseSessionRecord.toSourceWorkout() = SourceWorkout(
    route = when (val result = exerciseRouteResult) {
        is ExerciseRouteResult.Data -> result.exerciseRoute.toRoute(startTime, endTime)

        is ExerciseRouteResult.NoData -> Route.EMPTY

        // Consent missing, or the app is in the background: the route exists but is not readable now.
        else -> null
    },
    sourceRecordId = metadata.id,
    origin = metadata.dataOrigin.packageName,
    device = metadata.device?.label(),
    activity = exerciseTypeName(exerciseType),
    title = title,
    start = startTime,
    end = endTime,
    zoneOffset = startZoneOffset,
    lastModified = metadata.lastModifiedTime,
    laps = laps.map(ExerciseLap::toLap),
    segments = segments.map(ExerciseSegment::toSegment),
)

internal fun Device.label(): String? = listOfNotNull(manufacturer, model).joinToString(" ").ifBlank { null }

internal fun ExerciseLap.toLap() = Lap(startTime, endTime, length?.inMeters)

internal fun ExerciseSegment.toSegment() = Segment(
    start = startTime,
    end = endTime,
    kind = when (segmentType) {
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL,
        -> SegmentKind.RUNNING

        ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE,
        ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
        -> SegmentKind.PAUSE

        else -> SegmentKind.OTHER
    },
    sourceType = segmentType,
    repetitions = repetitions,
)

internal fun ExerciseRoute.toRoute(from: Instant, to: Instant): Route {
    val points = route.filter { it.time in from..to }.sortedBy { it.time }.distinctBy { it.time }
    return Route(
        times = LongArray(points.size) { points[it].time.toEpochMilli() },
        latitudes = DoubleArray(points.size) { points[it].latitude },
        longitudes = DoubleArray(points.size) { points[it].longitude },
        altitudes = DoubleArray(points.size) { points[it].altitude?.inMeters ?: Double.NaN },
        accuracies = DoubleArray(points.size) { points[it].horizontalAccuracy?.inMeters ?: Double.NaN },
    )
}

/**
 * Turns (time, value) samples into a strictly increasing series clipped to [from, to]. Samples are
 * sorted, and when several share a timestamp the last one wins.
 */
internal fun sampleSeries(samples: List<Pair<Instant, Double>>, from: Instant, to: Instant): SampleSeries {
    val byTime = java.util.TreeMap<Long, Double>()
    for ((time, value) in samples) if (time in from..to) byTime[time.toEpochMilli()] = value
    return SampleSeries(byTime.keys.toLongArray(), byTime.values.toDoubleArray())
}

internal fun heartRateSeries(samples: List<Pair<Instant, Long>>, from: Instant, to: Instant): HeartRateSeries {
    val byTime = java.util.TreeMap<Long, Int>()
    for ((time, bpm) in samples) if (time in from..to) byTime[time.toEpochMilli()] = bpm.toInt()
    return HeartRateSeries(byTime.keys.toLongArray(), byTime.values.toIntArray())
}

/** The part of [value], recorded evenly over [start, end], that falls within [from, to]. */
internal fun proportionalShare(value: Double, start: Instant, end: Instant, from: Instant, to: Instant): Double {
    val span = end.toEpochMilli() - start.toEpochMilli()
    if (span <= 0) return if (start in from..to) value else 0.0
    val overlap = minOf(end, to).toEpochMilli() - maxOf(start, from).toEpochMilli()
    return if (overlap <= 0) 0.0 else value * overlap / span
}

internal fun Record.toMeasurement(): Measurement? = when (this) {
    is RestingHeartRateRecord -> measurement(MeasurementKind.RESTING_HEART_RATE, time, zoneOffset, beatsPerMinute.toDouble())
    is HeartRateVariabilityRmssdRecord -> measurement(MeasurementKind.HRV_RMSSD, time, zoneOffset, heartRateVariabilityMillis)
    is Vo2MaxRecord -> measurement(MeasurementKind.VO2_MAX, time, zoneOffset, vo2MillilitersPerMinuteKilogram)
    is WeightRecord -> measurement(MeasurementKind.WEIGHT, time, zoneOffset, weight.inKilograms)
    is RespiratoryRateRecord -> measurement(MeasurementKind.RESPIRATORY_RATE, time, zoneOffset, rate)
    is OxygenSaturationRecord -> measurement(MeasurementKind.OXYGEN_SATURATION, time, zoneOffset, percentage.value)
    else -> null
}

private fun Record.measurement(kind: MeasurementKind, time: Instant, zoneOffset: java.time.ZoneOffset?, value: Double) =
    Measurement(metadata.id, kind, time, zoneOffset, value, metadata.dataOrigin.packageName)

internal fun SleepSessionRecord.toSleepSession() = SleepSession(
    sourceRecordId = metadata.id,
    start = startTime,
    end = endTime,
    zoneOffset = startZoneOffset,
    origin = metadata.dataOrigin.packageName,
    stages = stages.map { SleepStage(it.startTime, it.endTime, sleepStageType(it.stage)) },
)

internal fun sleepStageType(stage: Int): SleepStageType = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> SleepStageType.AWAKE
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStageType.ASLEEP_UNSPECIFIED
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStageType.OUT_OF_BED
    SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStageType.LIGHT
    SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStageType.DEEP
    SleepSessionRecord.STAGE_TYPE_REM -> SleepStageType.REM
    else -> SleepStageType.UNKNOWN
}
