package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import java.time.LocalDate
import kotlin.math.roundToInt

/** The highest sustained heart rate of one workout. */
data class HeartRatePeak(val workoutId: String, val date: LocalDate, val bpm: Double)

/**
 * Maximal heart rate estimated from the athlete's own workouts: the highest heart rate held for
 * [WINDOW_SECONDS] seconds, confirmed by a second workout that came within [CONFIRMATION_BPM] of it.
 *
 * Age formulas such as Tanaka's (208 - 0.7 x age) miss individuals by 7 to 10 bpm either way, while
 * a hard session of one's own shows the heart's actual ceiling. Averaging over 10 seconds keeps single
 * optical spikes out, and the confirmation keeps one glitch from setting the value. The result is a
 * lower bound: a maximal test can only show a higher maximum.
 */
data class MaxHeartRateEstimate(val bpm: Int, val reachedOn: LocalDate, val workouts: Int) {
    companion object {
        const val WINDOW_SECONDS = 10
        const val CONFIRMATION_BPM = 3.0

        /** Workouts this far back still count; maximal heart rate falls by less than 1 bpm a year. */
        const val LOOKBACK_DAYS = 730L

        /** Samples further apart than this leave a gap rather than holding the last value. */
        private const val MAX_HOLD_MILLIS = 5_000L

        /** Peaks below this are easy sessions that say nothing about the maximum. */
        private const val MIN_PEAK_BPM = 120.0

        /**
         * The estimate from [peaks] on [today], or null when no peak is confirmed by another workout or
         * the result is outside the plausible range.
         */
        fun of(peaks: List<HeartRatePeak>, today: LocalDate): MaxHeartRateEstimate? {
            val recent = peaks.filter { it.date > today.minusDays(LOOKBACK_DAYS) && !it.date.isAfter(today) && it.bpm >= MIN_PEAK_BPM }
                .sortedByDescending { it.bpm }
            val confirmed = recent.withIndex().firstOrNull { (i, peak) ->
                recent.withIndex().any { (j, other) -> j != i && other.bpm >= peak.bpm - CONFIRMATION_BPM }
            }?.value ?: return null
            val bpm = confirmed.bpm.roundToInt()
            if (bpm !in HeartRateAnchors.MAX_HR_RANGE) return null
            return MaxHeartRateEstimate(bpm, confirmed.date, recent.size)
        }

        /**
         * The highest average over [WINDOW_SECONDS] consecutive seconds of [series], or null when it never
         * holds data that long. Samples are held for up to five seconds to fill a one-second grid.
         */
        fun sustainedPeak(series: HeartRateSeries): Double? {
            if (series.size < 2) return null
            val start = series.times.first()
            val seconds = ((series.times.last() - start) / 1_000).toInt() + 1
            val grid = IntArray(seconds) { -1 }
            var j = 0
            for (s in 0 until seconds) {
                val t = start + s * 1_000L
                while (j + 1 < series.size && series.times[j + 1] <= t) j++
                if (t - series.times[j] <= MAX_HOLD_MILLIS) grid[s] = series.bpm[j]
            }
            var best: Double? = null
            var sum = 0
            var run = 0
            for (s in 0 until seconds) {
                if (grid[s] < 0) {
                    sum = 0
                    run = 0
                    continue
                }
                sum += grid[s]
                run++
                if (run > WINDOW_SECONDS) {
                    sum -= grid[s - WINDOW_SECONDS]
                    run = WINDOW_SECONDS
                }
                if (run == WINDOW_SECONDS) {
                    val average = sum.toDouble() / WINDOW_SECONDS
                    if (best == null || average > best) best = average
                }
            }
            return best
        }
    }
}
