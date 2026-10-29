package app.pacinglab.core.analytics.recovery

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** The measures behind sleep health, each with its unit. */
enum class SleepHealthMeasureKind {
    /** Sleep Regularity Index, -100 to 100. */
    REGULARITY,

    /** Average time asleep as a share of need. */
    DURATION,

    /** Median sleep efficiency, a share of time in bed. */
    EFFICIENCY,

    /** Minutes the usual middle of the night lies outside 02:00 to 04:00. */
    TIMING,
}

data class SleepHealthMeasure(val kind: SleepHealthMeasureKind, val value: Double, val fraction: Double) {
    val withinThreshold: Boolean get() = fraction >= Grading.AT_THRESHOLD - 1e-9
}

/**
 * Sleep habits over the last two weeks, out of 100: the dimensions of sleep health that can be measured
 * from a watch (Buysse 2014: regularity, timing, efficiency, duration; satisfaction and alertness need
 * the athlete's own report), counted equally as in the composite sleep health scores that predict
 * mortality and disease (Wallace 2018; Brindle 2019). Where the night's [SleepScore] says how rested
 * the athlete is today, this says whether the habits are healthy in the long run.
 * - Regularity (25): the Sleep Regularity Index ([regularityIndex]); excellent from 87, threshold 74,
 *   nothing below 50. In the UK Biobank the median was 81 (interquartile range 74 to 86) and regularity
 *   predicted mortality more strongly than duration (Windred 2024).
 * - Duration (25): average time asleep against need; threshold 90 %, excellent at the need, nothing
 *   below 75 %. Habitually short sleep raises the risk of disease (Cappuccio 2010).
 * - Efficiency (25): the median night's; threshold 85 % (Ohayon 2017), excellent 95 %.
 * - Timing (25): the usual middle of the night within 02:00 to 04:00 (Buysse 2014); threshold an hour
 *   outside it, nothing beyond two and a half hours.
 *
 * Parts that cannot be measured are left out and the rest scaled up to 100.
 *
 * @property usualMidpointMinute the usual middle of the night, minutes after midnight.
 */
data class SleepHealth(val score: Int, val nights: Int, val usualMidpointMinute: Int, val measures: List<SleepHealthMeasure>) {
    fun measure(kind: SleepHealthMeasureKind): SleepHealthMeasure? = measures.firstOrNull { it.kind == kind }

    val rating: SleepRating get() = ratingOf(score)

    companion object {
        /** Nights the two weeks need before a score is given. */
        const val MIN_NIGHTS = 5

        /** Pairs of consecutive nights the regularity index needs. */
        const val MIN_PAIRS = 4

        const val WINDOW_DAYS = 14L

        /** The sleep health window for the middle of the night, in minutes after midnight. */
        const val TIMING_FROM = 2 * 60
        const val TIMING_TO = 4 * 60

        private const val DAY_MINUTES = 24 * 60
        private const val MINUTE_MILLIS = 60_000L
        private const val DAY_MILLIS = DAY_MINUTES * MINUTE_MILLIS

        /** Sleep health on every morning from the first with enough nights; [needs] by morning. */
        fun all(nights: List<Night>, needs: Map<LocalDate, Long>): Map<LocalDate, SleepHealth> {
            val sorted = nights.sortedBy { it.date }
            val agreement = pairAgreements(sorted)
            return sorted.mapNotNull { night -> of(sorted, needs, night.date, agreement)?.let { night.date to it } }.toMap()
        }

        /** Sleep health over the [WINDOW_DAYS] up to [date]; null with fewer than [MIN_NIGHTS] nights. */
        fun of(nights: List<Night>, needs: Map<LocalDate, Long>, date: LocalDate): SleepHealth? =
            of(nights.sortedBy { it.date }, needs, date, pairAgreements(nights.sortedBy { it.date }))

        private fun of(sorted: List<Night>, needs: Map<LocalDate, Long>, date: LocalDate, agreement: Map<LocalDate, Double>): SleepHealth? {
            val window = sorted.filter { it.date <= date && it.date > date.minusDays(WINDOW_DAYS) }
            if (window.size < MIN_NIGHTS) return null
            val measures = mutableListOf<SleepHealthMeasure>()
            fun measure(kind: SleepHealthMeasureKind, value: Double, best: Double, threshold: Double, worst: Double) {
                measures += SleepHealthMeasure(kind, value, Grading.graded(value, best, threshold, worst))
            }
            regularity(window, agreement)?.let {
                measure(SleepHealthMeasureKind.REGULARITY, it, best = 87.0, threshold = 74.0, worst = 50.0)
            }
            val shares = window.mapNotNull { night -> needs[night.date]?.let { night.asleepMillis.toDouble() / it } }
            if (shares.isNotEmpty()) measure(SleepHealthMeasureKind.DURATION, shares.average(), best = 1.0, threshold = 0.9, worst = 0.75)
            Nights.median(window.filter { it.continuity != null }.map { it.asleepMillis.toDouble() / it.inBedMillis })?.let {
                measure(SleepHealthMeasureKind.EFFICIENCY, it, best = 0.95, threshold = 0.85, worst = 0.75)
            }
            val usual = circularMedianMinute(window.map { it.midpointMinuteOfDay })
            measure(SleepHealthMeasureKind.TIMING, minutesOutsideWindow(usual).toDouble(), best = 0.0, threshold = 60.0, worst = 150.0)
            val score = measures.sumOf { it.fraction } / measures.size * 100
            return SleepHealth(score.roundToInt(), window.size, usual, measures)
        }

        /**
         * The Sleep Regularity Index of [window] (Phillips 2017): the chance that the athlete is in the
         * same state, asleep or awake, at any two moments 24 hours apart, scaled so that 100 is the same
         * schedule every day and 0 a random one: 200 x agreement - 100. Worked out minute by minute over
         * each pair of consecutive mornings that both have a night; null with fewer than [MIN_PAIRS].
         * Naps are not part of it: only the main sleep is kept.
         */
        fun regularityIndex(window: List<Night>): Double? =
            regularity(window.sortedBy { it.date }, pairAgreements(window.sortedBy { it.date }))

        private fun regularity(window: List<Night>, agreement: Map<LocalDate, Double>): Double? {
            val pairs = window.mapNotNull { agreement[it.date] }.takeIf { it.size >= MIN_PAIRS } ?: return null
            return 200 * pairs.average() - 100
        }

        /**
         * For each night that follows the night of the previous morning: the share of the minutes of the
         * 24 hours from noon before the earlier night in which the state matched the same minute a day
         * later. Keyed by the later morning.
         */
        private fun pairAgreements(sorted: List<Night>): Map<LocalDate, Double> {
            val byDate = sorted.associateBy { it.date }
            return sorted.mapNotNull { later ->
                val earlier = byDate[later.date.minusDays(1)] ?: return@mapNotNull null
                val from = later.date.minusDays(2).atTime(LocalTime.NOON).atZone(earlier.zone).toInstant().toEpochMilli()
                val spans = sorted
                    .filter { it.end.toEpochMilli() > from && it.start.toEpochMilli() < from + 2 * DAY_MILLIS }
                    .flatMap { it.asleepSpans() }
                val asleep = BooleanArray(2 * DAY_MINUTES)
                for (span in spans) {
                    val first = ((span.first - from) / MINUTE_MILLIS).coerceAtLeast(0)
                    val last = ((span.last - from) / MINUTE_MILLIS).coerceAtMost(2L * DAY_MINUTES - 1)
                    for (m in first..last) asleep[m.toInt()] = true
                }
                val same = (0 until DAY_MINUTES).count { asleep[it] == asleep[it + DAY_MINUTES] }
                later.date to same.toDouble() / DAY_MINUTES
            }.toMap()
        }

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
    }
}
