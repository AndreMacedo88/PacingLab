package app.pacinglab.core.analytics.performance

import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import kotlin.math.abs

/**
 * VO2max estimated from one run.
 *
 * @property windows steady stretches the estimate rests on; more means a steadier estimate.
 */
data class RunVo2MaxEstimate(val value: Double, val windows: Int)

/**
 * VO2max from submaximal running, the way watches estimate it, with every step visible:
 *
 * 1. Find steady stretches: four minutes whose two halves differ in pace by less than 5 % and whose
 *    last minute of heart rate has levelled off (the two half-minutes within 1.5 bpm). Heart rate needs
 *    about three minutes at an even pace to settle, so short warm-ups give nothing; the first two
 *    minutes of a run are skipped as well.
 * 2. The oxygen cost of the last minute's speed by the ACSM running equation:
 *    VO2 = 3.5 + 0.2 x speed (m/min), in ml/kg/min, on the flat.
 * 3. The share of heart rate reserve used, which matches the share of VO2 reserve (Swain and Leutholtz
 *    1997): VO2max = 3.5 + (VO2 - 3.5) / %HRR. Only stretches between 60 and 97 % of reserve count;
 *    below that heart rate says little about effort.
 *
 * The run's estimate is the median over its stretches. It depends on correct maximal and resting
 * heart rate and assumes average running economy and flat ground.
 */
object RunVo2Max {
    const val WINDOW_SECONDS = 240
    const val STEP_SECONDS = 15
    const val SKIP_SECONDS = 120
    const val MIN_WINDOWS = 3

    private const val PACE_DRIFT = 0.05
    private const val HR_DRIFT_BPM = 1.5
    private const val MIN_SPEED = 2.3
    private const val MAX_SPEED = 7.0
    private const val MIN_RESERVE = 0.6
    private const val MAX_RESERVE = 0.97
    private const val RESTING_VO2 = 3.5

    /** Estimate for a run between [startMillis] and [endMillis], or null without enough steady running. */
    fun of(
        heartRate: HeartRateSeries,
        speed: SampleSeries,
        startMillis: Long,
        endMillis: Long,
        anchors: HeartRateAnchors,
    ): RunVo2MaxEstimate? {
        val resting = anchors.restingHr ?: return null
        val seconds = ((endMillis - startMillis) / 1_000).toInt()
        if (seconds < SKIP_SECONDS + WINDOW_SECONDS) return null
        val hr = secondGrid(heartRate.times, DoubleArray(heartRate.size) { heartRate.bpm[it].toDouble() }, startMillis, seconds)
        val v = secondGrid(speed.times, speed.values, startMillis, seconds)
        val estimates = mutableListOf<Double>()
        var i = SKIP_SECONDS
        while (i + WINDOW_SECONDS <= seconds) {
            window(hr, v, i, resting, anchors.maxHr)?.let(estimates::add)
            i += STEP_SECONDS
        }
        if (estimates.size < MIN_WINDOWS) return null
        estimates.sort()
        val middle = estimates.size / 2
        val median = if (estimates.size % 2 == 1) estimates[middle] else (estimates[middle - 1] + estimates[middle]) / 2
        return RunVo2MaxEstimate(median, estimates.size)
    }

    private fun window(hr: DoubleArray, speed: DoubleArray, from: Int, resting: Int, max: Int): Double? {
        val to = from + WINDOW_SECONDS
        val half = WINDOW_SECONDS / 2
        if ((from until to).any { speed[it].isNaN() } || (to - 60 until to).any { hr[it].isNaN() }) return null
        val first = mean(speed, from, from + half)
        val second = mean(speed, from + half, to)
        val pace = (first + second) / 2
        if (pace !in MIN_SPEED..MAX_SPEED || abs(first - second) / pace > PACE_DRIFT) return null
        if (abs(mean(hr, to - 30, to) - mean(hr, to - 60, to - 30)) > HR_DRIFT_BPM) return null
        val reserve = (mean(hr, to - 60, to) - resting) / (max - resting)
        if (reserve !in MIN_RESERVE..MAX_RESERVE) return null
        val vo2 = RESTING_VO2 + 0.2 * mean(speed, to - 60, to) * 60
        return RESTING_VO2 + (vo2 - RESTING_VO2) / reserve
    }

    private fun mean(values: DoubleArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (k in from until to) sum += values[k]
        return sum / (to - from)
    }
}
