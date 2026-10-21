package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStageType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One night: the main sleep and what the watch measured during it, dated by the morning it ended.
 *
 * @property asleepMillis time asleep (light, deep, REM or unspecified sleep); the whole session when
 *   it has no stages.
 * @property hrvMs heart rate variability (RMSSD) of the night: the median of the watch's five-minute
 *   values during the sleep, which is how Fitbit reports its nightly HRV.
 * @property restingHr resting heart rate the watch derived from the sleep.
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
)

object Nights {
    /** Sleeps shorter than this are naps. */
    val MIN_NIGHT: Duration = Duration.ofHours(3)

    /** Fewer HRV values than this (half an hour of sleep) give no nightly HRV. */
    const val MIN_HRV_SAMPLES = 6

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
                Night(
                    date = date,
                    start = sleep.start,
                    end = sleep.end,
                    asleepMillis = asleep,
                    stageMillis = stageTotals(sleep),
                    hrvMs = median(hrvByTime.filter { it.time >= sleep.start && it.time <= sleep.end }.map { it.value }, MIN_HRV_SAMPLES),
                    restingHr = nearestToEnd(restingHr, sleep.end),
                    respiratoryRate = nearestToEnd(respiratoryRate, sleep.end),
                )
            }
            .sortedBy { it.date }
    }

    private fun asleep(sleep: SleepSession): Long = if (sleep.stages.isEmpty()) {
        Duration.between(sleep.start, sleep.end).toMillis()
    } else {
        sleep.stages.filter { it.type in ASLEEP }.sumOf { Duration.between(it.start, it.end).toMillis() }
    }

    private fun stageTotals(sleep: SleepSession): Map<SleepStageType, Long> =
        sleep.stages.groupBy { it.type }.mapValues { (_, stages) -> stages.sumOf { Duration.between(it.start, it.end).toMillis() } }

    private fun nearestToEnd(values: List<Measurement>, end: Instant): Double? = values
        .filter { Duration.between(it.time, end).abs() <= NIGHT_VALUE_TOLERANCE }
        .minByOrNull { Duration.between(it.time, end).abs() }
        ?.value

    internal fun median(values: List<Double>, minimum: Int = 1): Double? {
        if (values.size < minimum || values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
