package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.RangeStanding
import java.time.LocalDate
import kotlin.math.floor

/** A value measured once a night, compared night by night with the athlete's own usual range. */
enum class NightlyMetric(val windowDays: Long, val minHalfWidth: Double, val value: (Night) -> Double?) {
    HRV(60, 0.0, { it.hrvMs }),
    RESTING_HR(30, 2.0, { it.restingHr }),
    BREATHING_RATE(30, 0.5, { it.respiratoryRate }),
    SLEEPING_HR(30, 2.0, { it.sleepingHeartRate?.lowest }),
}

/**
 * The range a single night's value usually falls in: the 10th to 90th percentile of the athlete's own
 * nights in the [NightlyMetric.windowDays] days before, so that about four nights in five fall inside.
 * Values move in steps (whole beats, tenths of a breath), so the range is at least
 * [NightlyMetric.minHalfWidth] either side of the median.
 *
 * Single nights are noisy. Readiness judges HRV on its 7-day average against a much narrower band (the
 * smallest worthwhile change), which suits a trend but would flag one normal night in three.
 */
data class NightlyRange(val low: Double, val high: Double, val nights: Int) {
    fun standing(value: Double): RangeStanding = when {
        value < low -> RangeStanding.BELOW
        value > high -> RangeStanding.ABOVE
        else -> RangeStanding.IN_RANGE
    }

    companion object {
        /** Nights needed before a range is shown. */
        const val MIN_NIGHTS = 14

        private const val LOW_QUANTILE = 0.1
        private const val HIGH_QUANTILE = 0.9

        /** The range for the night of [date], from the nights before it; null with fewer than [MIN_NIGHTS]. */
        fun of(nights: List<Night>, metric: NightlyMetric, date: LocalDate): NightlyRange? {
            val from = date.minusDays(metric.windowDays)
            val values = nights.filter { it.date < date && it.date >= from }.mapNotNull(metric.value).sorted()
            if (values.size < MIN_NIGHTS) return null
            val median = quantile(values, 0.5)
            return NightlyRange(
                low = minOf(quantile(values, LOW_QUANTILE), median - metric.minHalfWidth),
                high = maxOf(quantile(values, HIGH_QUANTILE), median + metric.minHalfWidth),
                nights = values.size,
            )
        }

        /** Linear interpolation between the closest ranks of [sorted]. */
        internal fun quantile(sorted: List<Double>, q: Double): Double {
            val position = q * (sorted.size - 1)
            val below = floor(position).toInt()
            val above = minOf(below + 1, sorted.size - 1)
            return sorted[below] + (sorted[above] - sorted[below]) * (position - below)
        }
    }
}
