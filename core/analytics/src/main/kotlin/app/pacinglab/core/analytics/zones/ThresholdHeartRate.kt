package app.pacinglab.core.analytics.zones

import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import java.time.LocalDate
import kotlin.math.roundToInt

/** The hardest continuous half hour of one run, measured the way Friel's threshold test is. */
data class ThresholdEffort(val workoutId: String, val date: LocalDate, val bpm: Double)

/**
 * Lactate threshold heart rate estimated from the athlete's own runs, after Joe Friel's field test: in a
 * 30-minute solo effort at the highest pace that can be held, the average heart rate of the last 20
 * minutes is close to LTHR. PacingLab looks for such half hours in every run: 30 minutes of continuous
 * running (no walking or stops in any half minute) whose last 20 minutes averaged at least
 * [MIN_FRACTION_OF_MAX] of maximal heart rate, so that easy runs do not count. The hardest one of the
 * last [LOOKBACK_DAYS] days sets the value.
 *
 * A run that was not a maximal effort gives a value at or below the true threshold, so the estimate
 * only rises as harder efforts come in. Heat and long runs raise heart rate at a given effort (cardiac
 * drift), which can push it a few beats too high.
 *
 * @property measuredOn the date of the run that set the value.
 */
data class ThresholdHeartRateEstimate(val bpm: Int, val measuredOn: LocalDate) {
    companion object {
        const val EFFORT_SECONDS = 30 * 60
        const val MEASURED_SECONDS = 20 * 60
        const val LOOKBACK_DAYS = 365L
        const val MIN_FRACTION_OF_MAX = 0.85

        /** Slowest half-minute that still counts as running, m/s (7:35 per km). */
        private const val MIN_RUNNING_SPEED = 2.2
        private const val BLOCK_SECONDS = 30

        /** Share of the measured 20 minutes that must have heart rate. */
        private const val MIN_HR_COVERAGE = 0.9

        /**
         * The estimate on [date] for an athlete whose maximal heart rate is [maxHr], from [efforts] in the
         * [LOOKBACK_DAYS] days up to it; null when none was hard enough.
         */
        fun of(efforts: List<ThresholdEffort>, maxHr: Int, date: LocalDate): ThresholdHeartRateEstimate? = efforts
            .filter { it.date > date.minusDays(LOOKBACK_DAYS) && !it.date.isAfter(date) }
            .filter { it.bpm >= MIN_FRACTION_OF_MAX * maxHr && it.bpm.roundToInt() < maxHr }
            .maxByOrNull { it.bpm }
            ?.let { ThresholdHeartRateEstimate(it.bpm.roundToInt(), it.date) }

        /**
         * Average heart rate over the last 20 minutes of the hardest continuous 30 minutes of running
         * between [startMillis] and [endMillis], or null when the run never ran 30 minutes without a break.
         */
        fun effort(heartRate: HeartRateSeries, speed: SampleSeries, startMillis: Long, endMillis: Long): Double? {
            val seconds = ((endMillis - startMillis) / 1_000).toInt()
            if (seconds < EFFORT_SECONDS) return null
            val hr = secondGrid(heartRate.times, DoubleArray(heartRate.size) { heartRate.bpm[it].toDouble() }, startMillis, seconds)
            val v = secondGrid(speed.times, speed.values, startMillis, seconds)
            val blocks = seconds / BLOCK_SECONDS
            val running = BooleanArray(blocks) { b ->
                val from = b * BLOCK_SECONDS
                (from until from + BLOCK_SECONDS).none { v[it].isNaN() } &&
                    (from until from + BLOCK_SECONDS).sumOf { v[it] } / BLOCK_SECONDS >= MIN_RUNNING_SPEED
            }
            val effortBlocks = EFFORT_SECONDS / BLOCK_SECONDS
            var best: Double? = null
            var streak = 0
            for (b in 0 until blocks) {
                streak = if (running[b]) streak + 1 else 0
                if (streak < effortBlocks) continue
                val to = (b + 1) * BLOCK_SECONDS
                var sum = 0.0
                var count = 0
                for (s in to - MEASURED_SECONDS until to) {
                    if (!hr[s].isNaN()) {
                        sum += hr[s]
                        count++
                    }
                }
                if (count < MIN_HR_COVERAGE * MEASURED_SECONDS) continue
                val average = sum / count
                if (best == null || average > best) best = average
            }
            return best
        }
    }
}
