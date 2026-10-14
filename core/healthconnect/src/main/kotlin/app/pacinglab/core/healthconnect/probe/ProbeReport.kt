package app.pacinglab.core.healthconnect.probe

import kotlinx.serialization.Serializable

/**
 * Snapshot of what Health Connect holds for this user, used to decide how PacingLab ingests data.
 * Contains no coordinates and no raw values beyond a handful of recent daily readings.
 * Times are ISO-8601 instants.
 */
@Serializable
data class ProbeReport(
    val formatVersion: Int = FORMAT_VERSION,
    val generatedAt: String,
    val probeDurationMs: Long,
    val appVersion: String,
    val device: DeviceInfo,
    val healthConnect: HealthConnectInfo,
    val recordTypes: List<RecordTypeSummary>,
    val sessionTypeCounts: Map<String, Int>,
    val sessions: List<SessionSummary>,
    val allDayHeartRate: SeriesSummary?,
    val recentDailyValues: Map<String, List<TimedValue>>,
    val recentSleep: List<SleepSummary>,
    val errors: List<String>,
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class DeviceInfo(val manufacturer: String, val model: String, val sdkInt: Int, val release: String)

@Serializable
data class HealthConnectInfo(
    val availability: String,
    val historyFeature: Boolean,
    val backgroundFeature: Boolean,
    val granted: List<String>,
    val missing: List<String>,
)

/**
 * @property recordsLast30Days records whose time falls in the last 30 days.
 * @property samplesLast30Days for series records (heart rate, speed, ...), the number of samples.
 * @property medianRecordSpanS for interval records, the median length of one record: tells how
 *   the writing app chunks its data.
 */
@Serializable
data class RecordTypeSummary(
    val type: String,
    val granted: Boolean,
    val earliest: String? = null,
    val latest: String? = null,
    val recordsLast30Days: Int = 0,
    val samplesLast30Days: Int? = null,
    val medianRecordSpanS: Double? = null,
    val origins: Map<String, Int> = emptyMap(),
    val devices: Map<String, Int> = emptyMap(),
    val recordingMethods: Map<String, Int> = emptyMap(),
    val error: String? = null,
)

@Serializable
data class SessionSummary(
    val id: String,
    val start: String,
    val end: String,
    val zoneOffset: String?,
    val durationS: Long,
    val exerciseType: Int,
    val exerciseTypeName: String,
    val title: String?,
    val origin: String,
    val device: String?,
    val recordingMethod: String,
    val laps: Int,
    val segmentTypes: Map<Int, Int>,
    val route: RouteSummary,
    /** Route status as reported by the bulk read that listed the session (no single-record read). */
    val bulkRouteStatus: String? = null,
    val heartRate: SeriesSummary?,
    val speed: SeriesSummary?,
    val stepsCadence: SeriesSummary?,
    val power: SeriesSummary?,
    val distance: IntervalSummary?,
    val elevationGained: IntervalSummary?,
    val steps: IntervalSummary?,
    val activeCalories: IntervalSummary?,
    val errors: List<String>,
)

@Serializable
data class RouteSummary(
    val status: String,
    val points: Int = 0,
    val spacing: SpacingStats? = null,
    val pointsWithAltitude: Int = 0,
    val pointsWithHorizontalAccuracy: Int = 0,
    val medianHorizontalAccuracyM: Double? = null,
    val pathLengthM: Double? = null,
)

/**
 * @property firstOffsetS seconds from session start to the first sample (negative if earlier).
 * @property lastOffsetS seconds from session end to the last sample.
 */
@Serializable
data class SeriesSummary(
    val records: Int,
    val spacing: SpacingStats,
    val min: Double?,
    val max: Double?,
    val mean: Double?,
    val firstOffsetS: Long?,
    val lastOffsetS: Long?,
)

@Serializable
data class IntervalSummary(val records: Int, val total: Double, val medianRecordSpanS: Double?)

@Serializable
data class TimedValue(val time: String, val value: Double?, val note: String? = null)

@Serializable
data class SleepSummary(val start: String, val end: String, val stages: Map<Int, Int>, val origin: String)
