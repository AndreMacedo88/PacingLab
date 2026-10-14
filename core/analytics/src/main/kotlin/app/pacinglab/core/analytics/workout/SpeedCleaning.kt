package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport

object SpeedCleaning {
    /**
     * Highest believable speed per sport, m/s. GPS-derived speed from the Sense occasionally jumps to
     * tens of metres per second; anything above these limits is a measurement error.
     */
    fun maxPlausibleSpeed(sport: Sport): Double = when (sport) {
        Sport.RUN -> 11.0
        Sport.WALK -> 4.0
        Sport.RIDE -> 25.0
        Sport.STRENGTH, Sport.OTHER -> 11.0
    }

    /** Drops samples that are negative, not finite or faster than [maxSpeed]. */
    fun dropImplausible(speed: SampleSeries, maxSpeed: Double): SampleSeries {
        val keep = BooleanArray(speed.size) { speed.values[it].isFinite() && speed.values[it] in 0.0..maxSpeed }
        val count = keep.count { it }
        if (count == speed.size) return speed
        val times = LongArray(count)
        val values = DoubleArray(count)
        var j = 0
        for (i in 0 until speed.size) {
            if (keep[i]) {
                times[j] = speed.times[i]
                values[j] = speed.values[i]
                j++
            }
        }
        return SampleSeries(times, values)
    }

    /**
     * Distance covered according to the speed stream: each sample's speed holds until the next sample,
     * for at most [maxGapMillis].
     */
    fun integrateMeters(speed: SampleSeries, maxGapMillis: Long = 5_000): Double {
        var meters = 0.0
        for (i in 0 until speed.size - 1) {
            val dt = minOf(speed.times[i + 1] - speed.times[i], maxGapMillis)
            meters += speed.values[i] * dt / 1000.0
        }
        return meters
    }
}
