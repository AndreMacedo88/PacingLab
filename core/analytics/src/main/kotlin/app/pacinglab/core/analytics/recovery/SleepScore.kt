package app.pacinglab.core.analytics.recovery

import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SleepRating {
    POOR,
    FAIR,
    GOOD,
}

/** The measures behind a sleep score, each with its unit. */
enum class SleepMeasureKind {
    /** Time asleep as a share of need. */
    DURATION,

    /** Time asleep as a share of time in bed. */
    EFFICIENCY,

    /** Minutes awake between falling asleep and the final awakening. */
    AWAKE_AFTER_ONSET,

    /** Minutes from going to bed to falling asleep. */
    TIME_TO_SLEEP,

    /** Awakenings of five minutes or more. */
    LONG_AWAKENINGS,

    /** Minutes between the middle of the night and the usual one. */
    MIDPOINT_SHIFT,

    /** Minutes the middle of the night lies outside 02:00 to 04:00. */
    TIMING,
}

/**
 * One measure of a night: its [value] and how much of its points it earns ([fraction]); 0.6 or more is
 * within the published threshold.
 */
data class SleepMeasure(val kind: SleepMeasureKind, val value: Double, val fraction: Double) {
    val withinThreshold: Boolean get() = fraction >= SleepScore.AT_THRESHOLD - 1e-9
}

/**
 * A night's sleep scored out of 100 on the measurable dimensions of sleep health (Buysse 2014), each
 * part a fraction from 0 to 1 of its points. Every measure is graded: the published threshold of an
 * adequate night earns 60 % of its points, better values earn more up to an excellent value, worse
 * values less down to nothing ([graded]). A night that just meets every criterion therefore scores
 * about 80, and the score keeps telling good nights from very good ones.
 * - [duration] (40 points): time asleep against the athlete's need: full at the need, 60 % at 85 % of
 *   it, nothing at 60 %. Duration has the broadest evidence for health and performance.
 * - [continuity] (25 points): the National Sleep Foundation's quality criteria (Ohayon 2017) as
 *   thresholds: sleep efficiency 85 % (excellent 95 %), awake after falling asleep 20 minutes
 *   (excellent 5), time to fall asleep 30 minutes (excellent 10), one awakening of five minutes or
 *   more (excellent none).
 * - [regularity] (25 points): how far the middle of the night lies from the athlete's usual over the
 *   last two weeks: excellent within 10 minutes, threshold 45, nothing beyond two hours. Irregular
 *   sleep predicts mortality more strongly than short sleep (Windred 2024).
 * - [timing] (10 points): the middle of the night between 02:00 and 04:00, the sleep health criterion;
 *   60 % an hour outside it, nothing beyond two and a half hours.
 *
 * Sleep stages are not scored: wrist sensors tell them apart only roughly, and a single night's stage
 * shares say little about sleep quality. Brief arousals are not scored either; they are a normal part
 * of healthy sleep. Parts that cannot be measured (no stages, too few earlier nights) are left out
 * and the rest scaled up to 100.
 */
data class SleepScore(
    val score: Int,
    val duration: Double,
    val continuity: Double?,
    val regularity: Double?,
    val timing: Double,
    val midpointMinuteOfDay: Int,
    val midpointShiftMinutes: Int? = null,
    val measures: List<SleepMeasure> = emptyList(),
) {
    fun measure(kind: SleepMeasureKind): SleepMeasure? = measures.firstOrNull { it.kind == kind }

    val rating: SleepRating
        get() = when {
            score >= GOOD_FROM -> SleepRating.GOOD
            score >= FAIR_FROM -> SleepRating.FAIR
            else -> SleepRating.POOR
        }

    companion object {
        const val DURATION_POINTS = 40.0
        const val CONTINUITY_POINTS = 25.0
        const val REGULARITY_POINTS = 25.0
        const val TIMING_POINTS = 10.0

        const val GOOD_FROM = 80
        const val FAIR_FROM = 65

        /** Share of a part's points earned exactly at its threshold. */
        const val AT_THRESHOLD = 0.6

        /** Earlier nights needed before regularity counts. */
        const val REGULARITY_NIGHTS = 3

        /** Regularity compares with the nights of this many days before. */
        const val REGULARITY_WINDOW_DAYS = 14L

        /** The sleep health window for the middle of the night, in minutes after midnight. */
        const val TIMING_FROM = 2 * 60
        const val TIMING_TO = 4 * 60

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

        /**
         * Every night's score, each against the need and regularity of the nights before it; with
         * [fixedNeedMillis], against that need instead of the one worked out from the nights.
         */
        fun all(nights: List<Night>, fixedNeedMillis: Long? = null): Map<LocalDate, SleepScore> {
            val sorted = nights.sortedBy { it.date }
            return sorted.associate { it.date to of(it, fixedNeedMillis ?: need(sorted, it.date), sorted) }
        }

        /**
         * The score of [night] for an athlete who needs [needMillis] of sleep, with [earlier] nights (any
         * order; the last seven before [night] are used) for regularity.
         */
        fun of(night: Night, needMillis: Long, earlier: List<Night>): SleepScore {
            val measures = mutableListOf<SleepMeasure>()
            fun measure(kind: SleepMeasureKind, value: Double, best: Double, threshold: Double, worst: Double): Double =
                graded(value, best, threshold, worst).also { measures += SleepMeasure(kind, value, it) }

            val duration =
                measure(SleepMeasureKind.DURATION, night.asleepMillis.toDouble() / needMillis, best = 1.0, threshold = 0.85, worst = 0.6)
            val continuity = night.continuity?.let { c ->
                val efficiency = night.asleepMillis.toDouble() / night.inBedMillis
                (
                    9 * measure(SleepMeasureKind.EFFICIENCY, efficiency, best = 0.95, threshold = 0.85, worst = 0.75) +
                        8 *
                        measure(
                            SleepMeasureKind.AWAKE_AFTER_ONSET,
                            c.wakeAfterOnsetMillis / MINUTE,
                            best = 5.0,
                            threshold = 20.0,
                            worst = 50.0,
                        ) +
                        4 * measure(SleepMeasureKind.TIME_TO_SLEEP, c.latencyMillis / MINUTE, best = 10.0, threshold = 30.0, worst = 60.0) +
                        4 * measure(SleepMeasureKind.LONG_AWAKENINGS, c.longAwakenings.toDouble(), best = 0.0, threshold = 1.0, worst = 4.0)
                    ) / CONTINUITY_POINTS
            }
            val midpoint = minuteOfDay(night)
            val previous = earlier
                .filter { it.date < night.date && it.date >= night.date.minusDays(REGULARITY_WINDOW_DAYS) }
                .sortedBy { it.date }
                .takeLast(7)
            val shift = if (previous.size < REGULARITY_NIGHTS) {
                null
            } else {
                circularDistance(midpoint, circularMedianMinute(previous.map(::minuteOfDay)))
            }
            val regularity = shift?.let {
                measure(SleepMeasureKind.MIDPOINT_SHIFT, it.toDouble(), best = 10.0, threshold = 45.0, worst = 120.0)
            }
            val timing =
                measure(SleepMeasureKind.TIMING, minutesOutsideWindow(midpoint).toDouble(), best = 0.0, threshold = 60.0, worst = 150.0)
            val parts = listOfNotNull(
                duration to DURATION_POINTS,
                continuity?.let { it to CONTINUITY_POINTS },
                regularity?.let { it to REGULARITY_POINTS },
                timing to TIMING_POINTS,
            )
            val score = parts.sumOf { (f, points) -> f * points } / parts.sumOf { (_, points) -> points } * 100
            return SleepScore(score.roundToInt(), duration, continuity, regularity, timing, midpoint, shift, measures)
        }

        /**
         * 1 at [best] or better, [AT_THRESHOLD] at [threshold], 0 at [worst] or worse, linear in between;
         * works whichever direction is better.
         */
        internal fun graded(value: Double, best: Double, threshold: Double, worst: Double): Double {
            val higherIsBetter = best > worst
            fun beyond(a: Double, b: Double) = if (higherIsBetter) a >= b else a <= b
            return when {
                beyond(value, best) -> 1.0
                beyond(value, threshold) -> AT_THRESHOLD + (1 - AT_THRESHOLD) * (value - threshold) / (best - threshold)
                !beyond(value, worst) -> 0.0
                else -> AT_THRESHOLD * (value - worst) / (threshold - worst)
            }
        }

        private fun minuteOfDay(night: Night): Int = night.midpoint.atZone(night.zone).toLocalTime().toSecondOfDay() / 60

        /** Minutes between [minute] and the timing window, 0 inside it. */
        internal fun minutesOutsideWindow(minute: Int): Int = minOf(
            circularDistance(minute, TIMING_FROM),
            circularDistance(minute, TIMING_TO),
        ).takeIf { minute !in TIMING_FROM..TIMING_TO } ?: 0

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
