package app.pacinglab.core.analytics.performance

import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * How fast a run went for its heart rate at an easy effort, as the efficiency factor of endurance
 * coaching (Friel, Allen): speed divided by heart rate over steady easy running.
 *
 * @property speedAtReference speed in m/s at [AerobicEfficiency.REFERENCE_SHARE] of threshold heart rate,
 *   from the efficiency factor: the number to compare between runs.
 * @property windows steady stretches the value rests on.
 */
data class EfficiencyEstimate(val speedAtReference: Double, val windows: Int)

/** One run's efficiency on its date. */
data class EfficiencyPoint(val date: LocalDate, val speedAtReference: Double, val windows: Int, val wbgt: Double? = null) {
    /** Whether heat raised the value to stand for cool conditions ([app.pacinglab.core.analytics.weather.HeatAdjustment]). */
    val heatAdjusted: Boolean get() = wbgt != null && wbgt > app.pacinglab.core.analytics.weather.HeatAdjustment.OPTIMUM_UPPER
}

/** The trend on one day: the average of the runs in the [AerobicEfficiency.TREND_DAYS] days up to it. */
data class EfficiencyDay(val date: LocalDate, val speedAtReference: Double)

/**
 * Aerobic efficiency from steady easy running. Heart rate at a given pace drops as aerobic fitness
 * grows, so the speed a run holds per heartbeat rises with it; comparing runs only within the same band
 * of heart rate keeps the ratio fair, because it is not constant across intensities.
 *
 * 1. Steady stretches: two minutes whose halves differ in pace by less than 5 % and whose last minute
 *    of heart rate has levelled off (half-minutes within 1.5 bpm), after the first three minutes.
 * 2. Only stretches with heart rate between [MIN_SHARE] and [MAX_SHARE] of threshold heart rate count:
 *    easy running, where most of the athlete's running time is spent.
 * 3. The run's efficiency is the mean speed over the mean heart rate of those stretches, given back as
 *    the speed it means at [REFERENCE_SHARE] of threshold heart rate.
 *
 * Heat, hills, wind and fatigue lower it on a given day; the trend averages them out.
 */
object AerobicEfficiency {
    const val MIN_SHARE = 0.80
    const val MAX_SHARE = 0.90
    const val REFERENCE_SHARE = 0.85
    const val MIN_WINDOWS = 3
    const val TREND_DAYS = 42L

    private const val WINDOW_SECONDS = 120
    private const val STEP_SECONDS = 30
    private const val SKIP_SECONDS = 180
    private const val PACE_DRIFT = 0.05
    private const val HR_DRIFT_BPM = 1.5
    private const val MIN_SPEED = 2.0
    private const val MAX_SPEED = 6.0

    /** The heart rate the efficiency is expressed at, for an athlete with [thresholdHr]. */
    fun referenceHr(thresholdHr: Int): Int = Math.round(REFERENCE_SHARE * thresholdHr).toInt()

    fun of(heartRate: HeartRateSeries, speed: SampleSeries, startMillis: Long, endMillis: Long, thresholdHr: Int): EfficiencyEstimate? {
        val seconds = ((endMillis - startMillis) / 1_000).toInt()
        if (seconds < SKIP_SECONDS + WINDOW_SECONDS) return null
        val hr = secondGrid(heartRate.times, DoubleArray(heartRate.size) { heartRate.bpm[it].toDouble() }, startMillis, seconds)
        val v = secondGrid(speed.times, speed.values, startMillis, seconds)
        val low = MIN_SHARE * thresholdHr
        val high = MAX_SHARE * thresholdHr
        var speedSum = 0.0
        var hrSum = 0.0
        var windows = 0
        var i = SKIP_SECONDS
        while (i + WINDOW_SECONDS <= seconds) {
            val to = i + WINDOW_SECONDS
            val half = WINDOW_SECONDS / 2
            val complete = (i until to).none { v[it].isNaN() } && (to - 60 until to).none { hr[it].isNaN() }
            if (complete) {
                val first = mean(v, i, i + half)
                val second = mean(v, i + half, to)
                val pace = (first + second) / 2
                val heart = mean(hr, to - 60, to)
                val steady = pace in MIN_SPEED..MAX_SPEED && abs(first - second) / pace <= PACE_DRIFT &&
                    abs(mean(hr, to - 30, to) - mean(hr, to - 60, to - 30)) <= HR_DRIFT_BPM
                if (steady && heart in low..high) {
                    speedSum += mean(v, to - 60, to)
                    hrSum += heart
                    windows++
                }
            }
            i += STEP_SECONDS
        }
        if (windows < MIN_WINDOWS) return null
        return EfficiencyEstimate(speedSum / hrSum * REFERENCE_SHARE * thresholdHr, windows)
    }

    /**
     * The trend on every date that has runs in the [TREND_DAYS] before it, from the first run to
     * [until]: the mean of those runs, each weighted by its stretches.
     */
    fun trend(points: List<EfficiencyPoint>, until: LocalDate): List<EfficiencyDay> {
        val first = points.minOfOrNull { it.date } ?: return emptyList()
        val days = ChronoUnit.DAYS.between(first, until).toInt() + 1
        return (0 until days).mapNotNull { offset ->
            val date = first.plusDays(offset.toLong())
            val recent = points.filter { it.date <= date && it.date > date.minusDays(TREND_DAYS) }
            if (recent.isEmpty()) {
                null
            } else {
                EfficiencyDay(date, recent.sumOf { it.speedAtReference * it.windows } / recent.sumOf { it.windows })
            }
        }
    }

    private fun mean(values: DoubleArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (k in from until to) sum += values[k]
        return sum / (to - from)
    }
}
