package app.pacinglab.core.analytics.performance

import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import java.time.LocalDate
import kotlin.math.sqrt

/** Average speed (m/s) and heart rate over the moving seconds of half a run. */
data class RunHalf(val speed: Double, val bpm: Double) {
    /** Speed per beat, the efficiency factor. */
    val efficiency: Double get() = speed / bpm
}

/**
 * Pace to heart rate decoupling of one steady run.
 *
 * @property percent how much less speed each heartbeat bought in the second half than in the first;
 *   positive when heart rate drifted up or pace dropped.
 * @property minutes the analysed time, after the warm-up.
 */
data class Decoupling(val percent: Double, val firstHalf: RunHalf, val secondHalf: RunHalf, val minutes: Int)

/** One run's decoupling, for the trend. */
data class DecouplingPoint(val date: LocalDate, val workoutId: String, val percent: Double, val wbgt: Double? = null) {
    /**
     * Whether the run was warm enough for heat to add to the drift (WBGT from 18 °C, ACSM's moderate band).
     * There is no published rate to take the heat's share out of decoupling, so warm runs are marked
     * rather than corrected.
     */
    val warm: Boolean get() = wbgt != null && wbgt >= WARM_FROM

    companion object {
        const val WARM_FROM = 18.0
    }
}

/**
 * Aerobic decoupling (Pa:HR, Friel): on a long steady run, heart rate drifts up at an unchanged pace once
 * aerobic endurance runs short. The run after its first [WARM_UP_SECONDS] is split in two halves of equal
 * time, and speed per heartbeat is compared. Under [WELL_COUPLED] percent counts as good aerobic
 * endurance for that duration.
 *
 * Only steady running qualifies: at least [MIN_ANALYSED_SECONDS] after the warm-up, stopped (below
 * [MOVING_SPEED]) no more than [MAX_STOPPED_SHARE] of the time, and minute speeds varying by no more than
 * [MAX_SPEED_VARIATION] (coefficient of variation). Hills and heat also raise heart rate; the result
 * describes the run, not only the runner.
 */
object AerobicDecoupling {
    const val WELL_COUPLED = 5.0
    private const val WARM_UP_SECONDS = 600
    private const val MIN_ANALYSED_SECONDS = 1_800
    private const val MOVING_SPEED = 1.5
    private const val MAX_STOPPED_SHARE = 0.1
    private const val MAX_SPEED_VARIATION = 0.15
    private const val MIN_HEART_RATE_SHARE = 0.8

    fun of(heartRate: HeartRateSeries, speed: SampleSeries, startMillis: Long, endMillis: Long): Decoupling? {
        val seconds = ((endMillis - startMillis) / 1000).toInt() - WARM_UP_SECONDS
        if (seconds < MIN_ANALYSED_SECONDS || speed.isEmpty() || heartRate.isEmpty()) return null
        val from = startMillis + WARM_UP_SECONDS * 1_000L
        val v = secondGrid(speed.times, speed.values, from, seconds)
        val hr = secondGrid(heartRate.times, heartRate.bpm.map { it.toDouble() }.toDoubleArray(), from, seconds)
        val moving = BooleanArray(seconds) { v[it].isFinite() && v[it] >= MOVING_SPEED }
        if (moving.count { !it } > MAX_STOPPED_SHARE * seconds) return null
        if (hr.count { it.isFinite() } < MIN_HEART_RATE_SHARE * seconds) return null

        val minutes = (0 until seconds / 60).mapNotNull { m ->
            (m * 60 until (m + 1) * 60).filter { moving[it] }.takeIf { it.size >= 30 }?.map { v[it] }?.average()
        }
        val mean = minutes.average()
        val variation = sqrt(minutes.sumOf { (it - mean) * (it - mean) } / minutes.size) / mean
        if (variation > MAX_SPEED_VARIATION) return null

        fun half(range: IntRange): RunHalf? {
            val counted = range.filter { moving[it] && hr[it].isFinite() && hr[it] > 0 }
            if (counted.isEmpty()) return null
            return RunHalf(counted.sumOf { v[it] } / counted.size, counted.sumOf { hr[it] } / counted.size)
        }
        val first = half(0 until seconds / 2) ?: return null
        val second = half(seconds / 2 until seconds) ?: return null
        return Decoupling((first.efficiency - second.efficiency) / first.efficiency * 100, first, second, seconds / 60)
    }
}
