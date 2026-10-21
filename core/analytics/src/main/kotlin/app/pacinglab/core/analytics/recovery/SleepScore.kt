package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.SleepStageType
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SleepRating {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
}

/**
 * A night's sleep scored out of 100 from four parts, each a fraction from 0 to 1 of its points:
 * - [duration] (50 points): time asleep against the athlete's need; nothing below 60 % of it. Short
 *   sleep has the clearest effects on health and performance, so it weighs most, as in Fitbit's score.
 * - [continuity] (25 points): the National Sleep Foundation's quality criteria (Ohayon 2017): sleep
 *   efficiency of at least 85 %, at most 20 minutes awake after falling asleep, at most 30 minutes to
 *   fall asleep and at most one awakening of five minutes or more.
 * - [depth] (10 points): deep and REM sleep against typical adult shares (deep about 15 %, REM about
 *   20 % of sleep); wrist sensors tell stages apart only roughly, so this part weighs least.
 * - [regularity] (15 points): how far the middle of the night lies from the athlete's usual; sleep
 *   regularity predicts health independently of duration (Windred 2024).
 *
 * Parts that cannot be measured (no stages, too few earlier nights) are left out and the rest scaled
 * up to 100.
 */
data class SleepScore(
    val score: Int,
    val duration: Double,
    val continuity: Double?,
    val depth: Double?,
    val regularity: Double?,
    val midpointShiftMinutes: Int? = null,
) {
    val rating: SleepRating
        get() = when {
            score >= 90 -> SleepRating.EXCELLENT
            score >= 80 -> SleepRating.GOOD
            score >= 60 -> SleepRating.FAIR
            else -> SleepRating.POOR
        }

    companion object {
        const val DURATION_POINTS = 50.0
        const val CONTINUITY_POINTS = 25.0
        const val DEPTH_POINTS = 10.0
        const val REGULARITY_POINTS = 15.0

        /** Earlier nights needed before regularity counts. */
        const val REGULARITY_NIGHTS = 3

        val MIN_NEED: Duration = Duration.ofHours(7)
        val MAX_NEED: Duration = Duration.ofHours(9)

        /**
         * How much sleep the athlete needs going into the night of [date]: their usual time asleep over
         * the 30 nights up to then, kept between the 7 and 9 hours recommended for adults.
         */
        fun need(nights: List<Night>, date: LocalDate): Long {
            val usual = Nights.median(
                nights.filter {
                    it.date <= date && it.date > date.minusDays(NEED_WINDOW_DAYS)
                }.map { it.asleepMillis.toDouble() },
            )
            return (usual?.toLong() ?: MIN_NEED.toMillis()).coerceIn(MIN_NEED.toMillis(), MAX_NEED.toMillis())
        }

        /** Every night's score, each against the need and regularity of the nights before it. */
        fun all(nights: List<Night>): Map<LocalDate, SleepScore> {
            val sorted = nights.sortedBy { it.date }
            return sorted.associate { it.date to of(it, need(sorted, it.date), sorted) }
        }

        /**
         * The score of [night] for an athlete who needs [needMillis] of sleep, with [earlier] nights (any
         * order; the last seven before [night] are used) for regularity.
         */
        fun of(night: Night, needMillis: Long, earlier: List<Night>): SleepScore {
            val duration = fraction(night.asleepMillis.toDouble() / needMillis, good = 1.0, bad = 0.6)
            val continuity = night.continuity?.let { c ->
                val efficiency = night.asleepMillis.toDouble() / night.inBedMillis
                (
                    9 * fraction(efficiency, good = 0.85, bad = 0.74) +
                        8 * fraction(c.wakeAfterOnsetMillis / MINUTE, good = 20.0, bad = 51.0) +
                        4 * fraction(c.latencyMillis / MINUTE, good = 30.0, bad = 60.0) +
                        4 * fraction(c.longAwakenings.toDouble(), good = 1.0, bad = 4.0)
                    ) / CONTINUITY_POINTS
            }
            val depth = night.stageMillis.takeIf { (it[SleepStageType.DEEP] ?: 0) + (it[SleepStageType.REM] ?: 0) > 0 }?.let { stages ->
                val deep = (stages[SleepStageType.DEEP] ?: 0).toDouble() / night.asleepMillis
                val rem = (stages[SleepStageType.REM] ?: 0).toDouble() / night.asleepMillis
                (fraction(deep, good = 0.15, bad = 0.05) + fraction(rem, good = 0.20, bad = 0.10)) / 2
            }
            val previous = earlier.filter { it.date < night.date }.sortedBy { it.date }.takeLast(7)
            val shift = if (previous.size < REGULARITY_NIGHTS) {
                null
            } else {
                circularDistance(minuteOfDay(night), circularMedianMinute(previous.map(::minuteOfDay)))
            }
            val regularity = shift?.let { fraction(it.toDouble(), good = 30.0, bad = 120.0) }
            val parts = listOfNotNull(
                duration to DURATION_POINTS,
                continuity?.let { it to CONTINUITY_POINTS },
                depth?.let { it to DEPTH_POINTS },
                regularity?.let { it to REGULARITY_POINTS },
            )
            val score = parts.sumOf { (f, points) -> f * points } / parts.sumOf { (_, points) -> points } * 100
            return SleepScore(score.roundToInt(), duration, continuity, depth, regularity, shift)
        }

        /** 1 at [good] or better, 0 at [bad] or worse, linear between; works whichever way is better. */
        internal fun fraction(value: Double, good: Double, bad: Double): Double =
            if (good > bad) ((value - bad) / (good - bad)).coerceIn(0.0, 1.0) else ((bad - value) / (bad - good)).coerceIn(0.0, 1.0)

        private fun minuteOfDay(night: Night): Int = night.midpoint.atZone(night.zone).toLocalTime().toSecondOfDay() / 60

        /** Minutes between two clock times, the short way round midnight. */
        internal fun circularDistance(a: Int, b: Int): Int {
            val d = abs(a - b) % DAY_MINUTES
            return minOf(d, DAY_MINUTES - d)
        }

        /** The clock time with the smallest total distance to all [minutes]; handles nights around midnight. */
        internal fun circularMedianMinute(minutes: List<Int>): Int =
            minutes.minBy { candidate -> minutes.sumOf { circularDistance(it, candidate) } }

        private val MINUTE = Duration.ofMinutes(1).toMillis().toDouble()
        private const val DAY_MINUTES = 24 * 60
        private const val NEED_WINDOW_DAYS = 30L
    }
}
