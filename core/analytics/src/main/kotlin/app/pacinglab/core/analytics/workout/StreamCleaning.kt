package app.pacinglab.core.analytics.workout

import app.pacinglab.core.analytics.geo.haversineMeters
import app.pacinglab.core.analytics.geo.speeds
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.WorkoutStreams
import kotlin.math.abs

/**
 * Removes measurement errors from recorded streams before they are analysed. Streams are stored as
 * recorded; every analysis reads them through [clean].
 *
 * What the owner's recordings from 2025-2026 showed:
 * - Heart rate: no value outside 30-230 bpm and no isolated spike in 152,000 samples (Fitbit already
 *   smooths it). The filter stays as a guard against other sources and firmware.
 * - Speed: single samples up to 88 m/s in otherwise normal interval sessions. A sample that stands more
 *   than [SPEED_SPIKE] above the median of its five-sample neighbourhood is replaced by that median;
 *   sprints, which last three seconds or more, keep their speed.
 * - GPS: a few points that jump away and back (removed from the track), and stretches covered faster
 *   than a runner can move, mostly in a vehicle after the run (handled by [movingSpeeds]).
 *
 * Positions are not smoothed: smoothing shortens every run by 2-3 % and would set PacingLab's distances
 * apart from those of the watch, which sums the recorded points.
 */
object StreamCleaning {
    /** Speed a single GPS step may imply before it counts as an error or as not moving under one's own power, m/s. */
    fun maxStepSpeed(sport: Sport): Double = when (sport) {
        Sport.RUN -> 9.0
        Sport.WALK -> 4.0
        Sport.RIDE -> 25.0
        Sport.STRENGTH, Sport.OTHER -> 11.0
    }

    fun clean(streams: WorkoutStreams, sport: Sport): WorkoutStreams = streams.copy(
        heartRate = heartRate(streams.heartRate),
        speed = speed(streams.speed, SpeedCleaning.maxPlausibleSpeed(sport)),
        route = route(streams.route, maxStepSpeed(sport)),
    )

    /** Drops values outside [MIN_BPM]..[MAX_BPM] and flattens lone spikes against steady neighbours. */
    fun heartRate(series: HeartRateSeries): HeartRateSeries {
        if (series.isEmpty()) return series
        val keep = (0 until series.size).filter { series.bpm[it] in MIN_BPM..MAX_BPM }
        val times = LongArray(keep.size) { series.times[keep[it]] }
        val bpm = IntArray(keep.size) { series.bpm[keep[it]] }
        val cleaned = bpm.copyOf()
        for (i in HR_HALF_WINDOW until bpm.size - HR_HALF_WINDOW) {
            if (times[i + HR_HALF_WINDOW] - times[i - HR_HALF_WINDOW] > HR_MAX_SPAN_MILLIS) continue
            val neighbours = (i - HR_HALF_WINDOW..i + HR_HALF_WINDOW).filter { it != i }.map { bpm[it] }
            if (neighbours.max() - neighbours.min() > HR_STEADY_RANGE) continue
            val median = neighbours.sorted().let { (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
            if (abs(bpm[i] - median) >= HR_SPIKE) cleaned[i] = median
        }
        return if (keep.size == series.size && cleaned.contentEquals(series.bpm)) series else HeartRateSeries(times, cleaned)
    }

    /** Drops implausible samples and replaces one- or two-sample spikes with the local median. */
    fun speed(series: SampleSeries, maxSpeed: Double): SampleSeries {
        val plausible = SpeedCleaning.dropImplausible(series, maxSpeed)
        if (plausible.size < SPEED_WINDOW) return plausible
        val values = plausible.values
        val cleaned = values.copyOf()
        val half = SPEED_WINDOW / 2
        for (i in values.indices) {
            val from = maxOf(0, i - half)
            val until = minOf(values.size, i + half + 1)
            val window = values.copyOfRange(from, until).sorted()
            val median = window[window.size / 2]
            if (values[i] > median + SPEED_SPIKE) cleaned[i] = median
        }
        return if (cleaned.contentEquals(values)) plausible else SampleSeries(plausible.times, cleaned)
    }

    /**
     * Drops points that jump away from the track and straight back: the step to the point and the step
     * after it are both faster than [maxStepSpeed], while skipping the point is not.
     */
    fun route(route: Route, maxStepSpeed: Double): Route {
        if (route.size < 3) return route
        val keep = BooleanArray(route.size) { true }
        var previous = 0
        for (i in 1 until route.size - 1) {
            val spike = stepSpeed(route, previous, i) > maxStepSpeed &&
                stepSpeed(route, i, i + 1) > maxStepSpeed &&
                stepSpeed(route, previous, i + 1) <= maxStepSpeed
            if (spike) keep[i] = false else previous = i
        }
        if (keep.all { it }) return route
        val indices = keep.indices.filter { keep[it] }
        return Route(
            LongArray(indices.size) { route.times[indices[it]] },
            DoubleArray(indices.size) { route.latitudes[indices[it]] },
            DoubleArray(indices.size) { route.longitudes[indices[it]] },
            DoubleArray(indices.size) { route.altitudes[indices[it]] },
            DoubleArray(indices.size) { route.accuracies[indices[it]] },
        )
    }

    /**
     * Speed along the track with every step faster than [maxStepSpeed] left out, so that a GPS jump or a
     * ride in a vehicle reads as a pause rather than as fast running.
     */
    fun movingSpeeds(route: Route, maxStepSpeed: Double): SampleSeries = SpeedCleaning.dropImplausible(route.speeds(), maxStepSpeed)

    private fun stepSpeed(route: Route, from: Int, to: Int): Double {
        val seconds = (route.times[to] - route.times[from]) / 1000.0
        val meters = haversineMeters(route.latitudes[from], route.longitudes[from], route.latitudes[to], route.longitudes[to])
        return meters / seconds
    }

    const val MIN_BPM = 30
    const val MAX_BPM = 230
    private const val HR_HALF_WINDOW = 3
    private const val HR_MAX_SPAN_MILLIS = 15_000L
    private const val HR_STEADY_RANGE = 12
    private const val HR_SPIKE = 25
    private const val SPEED_WINDOW = 5
    private const val SPEED_SPIKE = 2.0
}
