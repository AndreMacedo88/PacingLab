package app.pacinglab.core.analytics.recovery

import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToInt

enum class SleepRating {
    POOR,
    FAIR,
    GOOD,
}

/** The measures behind a night's sleep score, each with its unit. */
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
}

/**
 * One measure of a night: its [value] and how much of its points it earns ([fraction]); 0.6 or more is
 * within the published threshold.
 */
data class SleepMeasure(val kind: SleepMeasureKind, val value: Double, val fraction: Double) {
    val withinThreshold: Boolean get() = fraction >= Grading.AT_THRESHOLD - 1e-9
}

/** Rating bands shared by the night's score and the two weeks' sleep health. */
fun ratingOf(score: Int): SleepRating = when {
    score >= SleepScore.GOOD_FROM -> SleepRating.GOOD
    score >= SleepScore.FAIR_FROM -> SleepRating.FAIR
    else -> SleepRating.POOR
}

/**
 * How restorative one night was, out of 100: what decides how rested and able the athlete is the next
 * day. Two parts, each a fraction from 0 to 1 of its points, every measure graded ([Grading.graded]):
 * the published threshold of an adequate night earns 60 % of its points, better values more up to an
 * excellent value, worse values less down to nothing.
 * - [duration] (60 points): time asleep against the athlete's need: full at the need, 60 % at 85 % of
 *   it, nothing at 60 %. Restricted sleep impairs the next day's attention and performance in proportion
 *   to the shortfall (Van Dongen 2003; Belenky 2003).
 * - [continuity] (40 points): the National Sleep Foundation's quality criteria (Ohayon 2017) as
 *   thresholds: sleep efficiency 85 % (excellent 95 %), awake after falling asleep 20 minutes
 *   (excellent 5), time to fall asleep 30 minutes (excellent 10), one awakening of five minutes or
 *   more (excellent none). Fragmented sleep impairs the next day much like shortened sleep (Stepanski
 *   2002).
 *
 * The habits that matter for long-term health (regularity, timing, the usual amount of sleep) are not a
 * property of one night; [SleepHealth] scores them over two weeks. Sleep stages are not scored: wrist
 * sensors tell them apart only roughly. A night without stages is scored on duration alone.
 */
data class SleepScore(val score: Int, val duration: Double, val continuity: Double?, val measures: List<SleepMeasure> = emptyList()) {
    fun measure(kind: SleepMeasureKind): SleepMeasure? = measures.firstOrNull { it.kind == kind }

    val rating: SleepRating get() = ratingOf(score)

    companion object {
        const val DURATION_POINTS = 60.0
        const val CONTINUITY_POINTS = 40.0

        const val GOOD_FROM = 80
        const val FAIR_FROM = 65

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

        /** Every night's score against its need ([need], or [fixedNeedMillis] when the athlete set one). */
        fun all(nights: List<Night>, fixedNeedMillis: Long? = null): Map<LocalDate, SleepScore> {
            val sorted = nights.sortedBy { it.date }
            return sorted.associate { it.date to of(it, fixedNeedMillis ?: need(sorted, it.date)) }
        }

        /** The score of [night] for an athlete who needs [needMillis] of sleep. */
        fun of(night: Night, needMillis: Long): SleepScore {
            val measures = mutableListOf<SleepMeasure>()
            fun measure(kind: SleepMeasureKind, value: Double, best: Double, threshold: Double, worst: Double): Double =
                Grading.graded(value, best, threshold, worst).also { measures += SleepMeasure(kind, value, it) }

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
                    ) / CONTINUITY_WEIGHTS
            }
            val parts = listOfNotNull(duration to DURATION_POINTS, continuity?.let { it to CONTINUITY_POINTS })
            val score = parts.sumOf { (f, points) -> f * points } / parts.sumOf { (_, points) -> points } * 100
            return SleepScore(score.roundToInt(), duration, continuity, measures)
        }

        private val MINUTE = Duration.ofMinutes(1).toMillis().toDouble()
        private const val CONTINUITY_WEIGHTS = 25.0
        private const val NEED_WINDOW_DAYS = 30L
    }
}

/** The grading shared by the sleep scores: thresholds earn 60 % of a measure's points. */
object Grading {
    /** Share of a part's points earned exactly at its threshold. */
    const val AT_THRESHOLD = 0.6

    /**
     * 1 at [best] or better, [AT_THRESHOLD] at [threshold], 0 at [worst] or worse, linear in between;
     * works whichever direction is better.
     */
    fun graded(value: Double, best: Double, threshold: Double, worst: Double): Double {
        val higherIsBetter = best > worst
        fun beyond(a: Double, b: Double) = if (higherIsBetter) a >= b else a <= b
        return when {
            beyond(value, best) -> 1.0
            beyond(value, threshold) -> AT_THRESHOLD + (1 - AT_THRESHOLD) * (value - threshold) / (best - threshold)
            !beyond(value, worst) -> 0.0
            else -> AT_THRESHOLD * (value - worst) / (threshold - worst)
        }
    }
}
