package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.DailyVitals
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One night: the main sleep and what the watch measured during it, dated by the morning it ended.
 *
 * @property asleepMillis time asleep (light, deep, REM or unspecified sleep); the whole session when
 *   it has no stages.
 * @property hrvMs heart rate variability (RMSSD) of the night: the watch app's own nightly value when the
 *   Google Health API supplies it, else the median of the watch's five-minute values during the sleep,
 *   which is how Fitbit reports its nightly HRV. Null when the values cover less than
 *   [Nights.MIN_HRV_COVERAGE] of the time asleep and the app's value is missing.
 * @property hrvPartial the watch shared five-minute HRV for only part of the night, too little for a
 *   nightly value.
 * @property restingHr resting heart rate the watch derived from the sleep.
 * @property continuity how unbroken the sleep was; null when the watch recorded no stages.
 * @property zone where the night was spent, for clock times.
 * @property stages the stages as the watch recorded them, in time order.
 * @property sleepingHeartRate heart rate during the sleep; null when it was not recorded.
 * @property oxygenSaturation average SpO2 of the night in per cent, from the Google Health API.
 * @property skinTemperature skin temperature relative to the watch app's baseline in degrees Celsius,
 *   from the Google Health API.
 */
data class Night(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val asleepMillis: Long,
    val stageMillis: Map<SleepStageType, Long>,
    val hrvMs: Double?,
    val restingHr: Double?,
    val respiratoryRate: Double?,
    val continuity: SleepContinuity? = null,
    val zone: ZoneId = ZoneOffset.UTC,
    val stages: List<SleepStage> = emptyList(),
    val sleepingHeartRate: SleepingHeartRate? = null,
    val hrvPartial: Boolean = false,
    val oxygenSaturation: Double? = null,
    val skinTemperature: Double? = null,
) {
    /** Time in bed: from the start of the sleep record to its end. */
    val inBedMillis: Long get() = Duration.between(start, end).toMillis()

    /** The middle of the time between falling asleep and the final awakening, for sleep regularity. */
    val midpoint: Instant get() = continuity?.let { Instant.ofEpochMilli((it.onset.toEpochMilli() + it.finalAwakening.toEpochMilli()) / 2) }
        ?: Instant.ofEpochMilli((start.toEpochMilli() + end.toEpochMilli()) / 2)
}

/**
 * The continuity measures of the National Sleep Foundation's sleep quality consensus (Ohayon 2017).
 *
 * @property latencyMillis from the start of the record to the first sleep stage.
 * @property wakeAfterOnsetMillis awake time between falling asleep and the final awakening.
 * @property longAwakenings awakenings of five minutes or more after falling asleep.
 */
data class SleepContinuity(
    val onset: Instant,
    val finalAwakening: Instant,
    val latencyMillis: Long,
    val wakeAfterOnsetMillis: Long,
    val longAwakenings: Int,
)

object Nights {
    /** Sleeps shorter than this are naps. */
    val MIN_NIGHT: Duration = Duration.ofHours(3)

    /** Awakenings at least this long count towards the consensus limit of one per night. */
    val LONG_AWAKENING: Duration = Duration.ofMinutes(5)

    /** Fewer HRV values than this (half an hour of sleep) give no nightly HRV. */
    const val MIN_HRV_SAMPLES = 6

    /**
     * Share of the time asleep the five-minute HRV values must cover for a nightly value. On complete
     * nights they cover 100-115 %. On 23 and 27 October 2026 Fitbit shared only the first one to three
     * hours (37 and 14 values, in Health Connect and in its own cloud alike) while its app showed a
     * value for the whole night; the median of those fragments was 46 and 33 ms against Fitbit's 71
     * and 58. A night below this share has no HRV rather than a wrong one.
     */
    const val MIN_HRV_COVERAGE = 0.6

    private const val HRV_WINDOW_MILLIS = 5 * 60_000L

    /**
     * Fitbit stamps nightly values (resting heart rate, breathing rate) with the end of the sleep. A
     * resting heart rate stamped elsewhere was derived from daytime wear and runs 10 to 20 bpm higher,
     * so only values this close to the end of a sleep belong to it.
     */
    val NIGHT_VALUE_TOLERANCE: Duration = Duration.ofHours(1)

    private val ASLEEP = setOf(SleepStageType.LIGHT, SleepStageType.DEEP, SleepStageType.REM, SleepStageType.ASLEEP_UNSPECIFIED)

    /**
     * The main sleep of each morning (the longest sleep ending that day, if it is not a nap), oldest
     * first, with its HRV, resting heart rate and breathing rate.
     */
    fun of(
        sleeps: List<SleepSession>,
        hrv: List<Measurement>,
        restingHr: List<Measurement>,
        respiratoryRate: List<Measurement>,
        zone: ZoneId,
    ): List<Night> {
        val hrvByTime = hrv.sortedBy { it.time }
        return sleeps
            .map { it to asleep(it) }
            .filter { (_, asleep) -> asleep >= MIN_NIGHT.toMillis() }
            .groupBy { (sleep, _) -> sleep.end.atZone(sleep.zoneOffset ?: zone).toLocalDate() }
            .map { (date, candidates) ->
                val (sleep, asleep) = candidates.maxBy { it.second }
                val hrvValues = hrvByTime.filter { it.time >= sleep.start && it.time <= sleep.end }.map { it.value }
                val covered = hrvValues.size * HRV_WINDOW_MILLIS >= MIN_HRV_COVERAGE * asleep
                Night(
                    date = date,
                    start = sleep.start,
                    end = sleep.end,
                    asleepMillis = asleep,
                    stageMillis = stageTotals(sleep),
                    hrvMs = if (covered) median(hrvValues, MIN_HRV_SAMPLES) else null,
                    hrvPartial = hrvValues.isNotEmpty() && !covered,
                    restingHr = nearestToEnd(restingHr, sleep.end),
                    respiratoryRate = nearestToEnd(respiratoryRate, sleep.end),
                    continuity = continuity(sleep),
                    zone = sleep.zoneOffset ?: zone,
                    stages = sleep.stages.sortedBy { it.start },
                    sleepingHeartRate = SleepingHeartRate.of(sleep.heartRate),
                )
            }
            .sortedBy { it.date }
    }

    /**
     * [nights] with the watch app's own values of the same mornings: its HRV replaces the median of the
     * 5-minute values (so both apps show the same number, partial nights included), its resting heart
     * rate and breathing rate fill gaps, and SpO2 and skin temperature are added.
     */
    fun withVitals(nights: List<Night>, vitals: List<DailyVitals>): List<Night> {
        if (vitals.isEmpty()) return nights
        val byDate = vitals.associateBy { it.date }
        return nights.map { night ->
            val day = byDate[night.date] ?: return@map night
            night.copy(
                hrvMs = day.hrvMs ?: night.hrvMs,
                hrvPartial = night.hrvPartial && day.hrvMs == null,
                restingHr = night.restingHr ?: day.restingHr,
                respiratoryRate = night.respiratoryRate ?: day.breathingRate,
                oxygenSaturation = day.oxygenSaturation,
                skinTemperature = day.skinTemperatureDeviation,
            )
        }
    }

    private fun asleep(sleep: SleepSession): Long = if (sleep.stages.isEmpty()) {
        Duration.between(sleep.start, sleep.end).toMillis()
    } else {
        sleep.stages.filter { it.type in ASLEEP }.sumOf { Duration.between(it.start, it.end).toMillis() }
    }

    private fun continuity(sleep: SleepSession): SleepContinuity? {
        val asleep = sleep.stages.filter { it.type in ASLEEP }
        if (asleep.isEmpty()) return null
        val onset = asleep.minOf { it.start }
        val finalAwakening = asleep.maxOf { it.end }
        // Consecutive awake stages form one awakening.
        val awakenings = mutableListOf<Long>()
        var current = 0L
        for (stage in sleep.stages.sortedBy { it.start }.filter { it.end > onset && it.start < finalAwakening }) {
            if (stage.type in ASLEEP) {
                if (current > 0) awakenings += current
                current = 0
            } else if (stage.type == SleepStageType.AWAKE || stage.type == SleepStageType.OUT_OF_BED) {
                current += Duration.between(maxOf(stage.start, onset), minOf(stage.end, finalAwakening)).toMillis()
            }
        }
        if (current > 0) awakenings += current
        return SleepContinuity(
            onset = onset,
            finalAwakening = finalAwakening,
            latencyMillis = Duration.between(sleep.start, onset).toMillis().coerceAtLeast(0),
            wakeAfterOnsetMillis = awakenings.sum(),
            longAwakenings = awakenings.count { it >= LONG_AWAKENING.toMillis() },
        )
    }

    private fun stageTotals(sleep: SleepSession): Map<SleepStageType, Long> =
        sleep.stages.groupBy { it.type }.mapValues { (_, stages) -> stages.sumOf { Duration.between(it.start, it.end).toMillis() } }

    private fun nearestToEnd(values: List<Measurement>, end: Instant): Double? = values
        .filter { Duration.between(it.time, end).abs() <= NIGHT_VALUE_TOLERANCE }
        .minByOrNull { Duration.between(it.time, end).abs() }
        ?.value

    /** The median of [values], or null with fewer than [minimum] of them. */
    fun median(values: List<Double>, minimum: Int = 1): Double? {
        if (values.size < minimum || values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
