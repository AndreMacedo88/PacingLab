package app.pacinglab.core.analytics.performance

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt

/** Where a VO2max value comes from, most trusted first. */
enum class Vo2MaxSource {
    /** A lab or field test the athlete entered. */
    TEST,

    /** Fitbit's estimate after a run, as written to Health Connect. */
    FITBIT,

    /** PacingLab's own estimate from a run ([RunVo2Max]). */
    PACINGLAB,
}

/**
 * One VO2max value in ml/kg/min.
 *
 * @property weight how many measurements it stands for; a PacingLab estimate resting on few steady
 *   stretches counts for less.
 */
data class Vo2MaxPoint(val date: LocalDate, val value: Double, val source: Vo2MaxSource, val weight: Double = 1.0)

/** The trend on one day: its best estimate and one standard deviation of uncertainty. */
data class Vo2MaxDay(val date: LocalDate, val value: Double, val sd: Double)

/**
 * VO2max over time from noisy estimates of several sources.
 *
 * @property days the smoothed trend, from the first estimate to the last day asked for, using every
 *   estimate before and after each day: the best picture of the past.
 * @property current today's value from the estimates up to today only, its uncertainty growing with
 *   every day without a new estimate.
 * @property sourceSd how much each source scatters around the trend, as learned from the data.
 */
data class Vo2MaxTrend(val days: List<Vo2MaxDay>, val current: Vo2MaxDay, val sourceSd: Map<Vo2MaxSource, Double>) {
    /** Change of the smoothed trend over the [days] before the last day; null when the trend is shorter. */
    fun change(days: Long): Double? {
        val last = this.days.lastOrNull() ?: return null
        val earlier = this.days.firstOrNull { it.date == last.date.minusDays(days) } ?: return null
        return last.value - earlier.value
    }

    companion object {
        /**
         * Day-to-day variance of true VO2max: about 1 ml/kg/min over a month, 2 over three months,
         * in line with how fast training changes it.
         */
        const val PROCESS_VARIANCE_PER_DAY = 0.04

        /** Starting assumptions for each source's scatter, before the data says otherwise. */
        val PRIOR_SD = mapOf(Vo2MaxSource.TEST to 1.0, Vo2MaxSource.FITBIT to 2.5, Vo2MaxSource.PACINGLAB to 3.0)

        /** Variance before the first estimate: 10 ml/kg/min either way. */
        private const val INITIAL_VARIANCE = 100.0

        /** How many points of its own a source needs to outweigh the prior scatter. */
        private const val PRIOR_POINTS = 5.0

        /**
         * A local-level Kalman filter with a Rauch-Tung-Striebel smoother over [points] up to [until].
         * Each source's measurement noise is learned from its scatter around a first pass, shrunk towards
         * [PRIOR_SD], so that consistent sources pull the trend harder. Null without points.
         */
        fun of(points: List<Vo2MaxPoint>, until: LocalDate): Vo2MaxTrend? {
            val usable = points.filter { !it.date.isAfter(until) && it.value.isFinite() && it.weight > 0 }
            if (usable.isEmpty()) return null
            var sd = PRIOR_SD
            repeat(2) {
                val pass = run(usable, until, sd)
                sd = Vo2MaxSource.entries.associateWith { source ->
                    val residuals = usable.filter { it.source == source }.map { p -> p.value - pass.smoothedAt(p.date) }
                    val prior = PRIOR_SD.getValue(source)
                    if (residuals.isEmpty()) {
                        prior
                    } else {
                        val variance = residuals.sumOf { it * it } / residuals.size
                        sqrt((residuals.size * variance + PRIOR_POINTS * prior * prior) / (residuals.size + PRIOR_POINTS))
                    }
                }
            }
            val pass = run(usable, until, sd)
            return Vo2MaxTrend(pass.smoothed, pass.filtered.last(), sd.filterKeys { source -> usable.any { it.source == source } })
        }

        private class Pass(val filtered: List<Vo2MaxDay>, val smoothed: List<Vo2MaxDay>) {
            private val byDate = smoothed.associateBy { it.date }

            fun smoothedAt(date: LocalDate): Double = byDate.getValue(date).value
        }

        private fun run(points: List<Vo2MaxPoint>, until: LocalDate, sd: Map<Vo2MaxSource, Double>): Pass {
            val start = points.minOf { it.date }
            val days = ChronoUnit.DAYS.between(start, until).toInt() + 1
            val byDay = points.groupBy { ChronoUnit.DAYS.between(start, it.date).toInt() }
            val filteredValue = DoubleArray(days)
            val filteredVariance = DoubleArray(days)
            val predictedValue = DoubleArray(days)
            val predictedVariance = DoubleArray(days)
            // A vague start: the first estimate alone sets the level.
            var x = byDay.getValue(0).first().value
            var p = INITIAL_VARIANCE
            for (d in 0 until days) {
                if (d > 0) p += PROCESS_VARIANCE_PER_DAY
                predictedValue[d] = x
                predictedVariance[d] = p
                for (point in byDay[d].orEmpty()) {
                    val r = sd.getValue(point.source).let { it * it } / point.weight
                    val gain = p / (p + r)
                    x += gain * (point.value - x)
                    p *= 1 - gain
                }
                filteredValue[d] = x
                filteredVariance[d] = p
            }
            val smoothedValue = filteredValue.copyOf()
            val smoothedVariance = filteredVariance.copyOf()
            for (d in days - 2 downTo 0) {
                val c = filteredVariance[d] / predictedVariance[d + 1]
                smoothedValue[d] = filteredValue[d] + c * (smoothedValue[d + 1] - predictedValue[d + 1])
                smoothedVariance[d] = filteredVariance[d] + c * c * (smoothedVariance[d + 1] - predictedVariance[d + 1])
            }
            fun day(i: Int, value: Double, variance: Double) = Vo2MaxDay(start.plusDays(i.toLong()), value, sqrt(variance))
            return Pass(
                filtered = List(days) { day(it, filteredValue[it], filteredVariance[it]) },
                smoothed = List(days) { day(it, smoothedValue[it], smoothedVariance[it]) },
            )
        }
    }
}
