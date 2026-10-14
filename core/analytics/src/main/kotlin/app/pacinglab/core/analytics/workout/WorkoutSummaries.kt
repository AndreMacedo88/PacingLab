package app.pacinglab.core.analytics.workout

import app.pacinglab.core.analytics.geo.lengthMeters
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.WorkoutSummary
import java.time.Duration
import kotlin.math.roundToInt

object WorkoutSummaries {
    /** Heart rate samples further apart than this do not contribute to the average. */
    private const val MAX_HR_GAP_MILLIS = 15_000L

    fun summarize(workout: SourceWorkout, streams: WorkoutStreams, sport: Sport): WorkoutSummary {
        val speed = SpeedCleaning.dropImplausible(streams.speed, SpeedCleaning.maxPlausibleSpeed(sport))
        // The recording app's own total is preferred: Fitbit fuses GPS with step-based distance before
        // the GPS fix, which the route and speed streams miss.
        val distance = streams.reportedDistanceMeters?.takeIf { it > 0 }
            ?: streams.route.takeIf { it.size >= 2 }?.lengthMeters()
            ?: speed.takeIf { it.size >= 2 }?.let { SpeedCleaning.integrateMeters(it) }
        return WorkoutSummary(
            durationMillis = Duration.between(workout.start, workout.end).toMillis(),
            distanceMeters = distance,
            averageHr = timeWeightedAverage(streams.heartRate),
            maxHr = spikeResistantMax(streams.heartRate),
            hasRoute = streams.route.size >= 2,
            hasSpeed = speed.size >= 2,
        )
    }

    /** Mean heart rate weighted by how long each sample holds, ignoring gaps (sensor dropouts). */
    fun timeWeightedAverage(hr: HeartRateSeries): Int? {
        var weighted = 0.0
        var total = 0L
        for (i in 0 until hr.size - 1) {
            val dt = hr.times[i + 1] - hr.times[i]
            if (dt <= MAX_HR_GAP_MILLIS) {
                weighted += hr.bpm[i].toDouble() * dt
                total += dt
            }
        }
        return if (total == 0L) hr.bpm.takeIf { it.isNotEmpty() }?.average()?.roundToInt() else (weighted / total).roundToInt()
    }

    /**
     * Highest heart rate that is not a single-sample spike: the maximum of a 3-sample running median.
     * Optical sensors occasionally report one implausible value; a real peak lasts several samples.
     */
    fun spikeResistantMax(hr: HeartRateSeries): Int? {
        if (hr.isEmpty()) return null
        if (hr.size < 3) return hr.bpm.max()
        var best = Int.MIN_VALUE
        for (i in 1 until hr.size - 1) {
            val a = hr.bpm[i - 1]
            val b = hr.bpm[i]
            val c = hr.bpm[i + 1]
            val median = maxOf(minOf(a, b), minOf(maxOf(a, b), c))
            if (median > best) best = median
        }
        return best
    }
}
