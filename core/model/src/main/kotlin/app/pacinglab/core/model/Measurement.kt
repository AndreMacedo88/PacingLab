package app.pacinglab.core.model

import java.time.Instant
import java.time.ZoneOffset

/** Single-value measurements kept as time series. Units are given per kind. */
enum class MeasurementKind {
    /** bpm */
    RESTING_HEART_RATE,

    /** RMSSD in milliseconds */
    HRV_RMSSD,

    /** ml/kg/min */
    VO2_MAX,

    /** kg */
    WEIGHT,

    /** breaths per minute */
    RESPIRATORY_RATE,

    /** percent */
    OXYGEN_SATURATION,
}

data class Measurement(
    val sourceRecordId: String,
    val kind: MeasurementKind,
    val time: Instant,
    val zoneOffset: ZoneOffset?,
    val value: Double,
    val origin: String,
)

enum class SleepStageType {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    ASLEEP_UNSPECIFIED,
    OUT_OF_BED,
    UNKNOWN,
}

data class SleepStage(val start: Instant, val end: Instant, val type: SleepStageType)

data class SleepSession(
    val sourceRecordId: String,
    val start: Instant,
    val end: Instant,
    val zoneOffset: ZoneOffset?,
    val origin: String,
    val stages: List<SleepStage>,
)
