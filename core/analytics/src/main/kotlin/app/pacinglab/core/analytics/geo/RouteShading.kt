package app.pacinglab.core.analytics.geo

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.ZoneSet
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pace along routes in [steps] even bands between [slowest] and [fastest] (m/s), one band per point.
 * Points slower or faster than the ends take the end bands.
 */
class PaceShading(val levels: List<IntArray>, val slowest: Double, val fastest: Double, val steps: Int)

/**
 * Levels for colouring a route: one per point, which colours the line from that point to the next.
 * [NONE] marks points without a value, drawn in a neutral colour.
 */
object RouteShading {
    const val NONE = -1

    /** Bands of the pace scale: enough to read as a gradient, few enough to tell neighbours apart. */
    const val PACE_STEPS = 7

    /** The zone of the heart rate nearest each point, within [MAX_HR_GAP_MILLIS]. */
    fun zones(routes: List<Route>, heartRate: HeartRateSeries, zones: ZoneSet): List<IntArray> = routes.map { route ->
        IntArray(route.size) { i -> nearestBpm(heartRate, route.times[i])?.let(zones::indexOf) ?: NONE }
    }

    /**
     * Pace of each point over the [WINDOW_MILLIS] around it, on a scale from the 5th to the 95th
     * percentile of the moving points (so that a single GPS jump or a stop at a crossing does not stretch
     * it), at least [MIN_SPAN] wide so that a steady run is not painted in every colour by GPS noise.
     * Steps faster than [maxStepSpeed], or across a gap in the recording, count neither distance nor time;
     * a point with none of its window left gets [NONE]. Null without enough moving points.
     */
    fun pace(routes: List<Route>, maxStepSpeed: Double, steps: Int = PACE_STEPS): PaceShading? {
        val speeds = routes.map { smoothedSpeeds(it, maxStepSpeed) }
        val moving = speeds.flatMap { s -> s.filter { it >= MIN_MOVING_SPEED } }.sorted()
        if (moving.size < MIN_MOVING_POINTS) return null
        var slowest = percentile(moving, LOW_PERCENTILE)
        var fastest = percentile(moving, HIGH_PERCENTILE)
        if (fastest < slowest * (1 + MIN_SPAN)) {
            val middle = sqrt(slowest * fastest)
            val half = sqrt(1 + MIN_SPAN)
            slowest = middle / half
            fastest = middle * half
        }
        val levels = speeds.map { route ->
            IntArray(route.size) { i ->
                val v = route[i]
                if (v.isNaN()) NONE else floor((v - slowest) / (fastest - slowest) * steps).toInt().coerceIn(0, steps - 1)
            }
        }
        return PaceShading(levels, slowest, fastest, steps)
    }

    /**
     * Speed at each point in m/s: the distance covered by the steps within half a window either side,
     * over their time; NaN where no step counts.
     */
    internal fun smoothedSpeeds(route: Route, maxStepSpeed: Double): DoubleArray {
        val n = route.size
        if (n < 2) return DoubleArray(n) { Double.NaN }
        val meters = DoubleArray(n - 1)
        val millis = LongArray(n - 1)
        for (k in 0 until n - 1) {
            val dt = route.times[k + 1] - route.times[k]
            val d = haversineMeters(route.latitudes[k], route.longitudes[k], route.latitudes[k + 1], route.longitudes[k + 1])
            if (dt in 1..MAX_STEP_GAP_MILLIS && d * MILLIS_PER_SECOND / dt <= maxStepSpeed) {
                meters[k] = d
                millis[k] = dt
            }
        }
        val half = WINDOW_MILLIS / 2
        val result = DoubleArray(n)
        // Steps lo until hi lie wholly within the window around point i; both ends only move forward.
        var lo = 0
        var hi = 0
        var sumMeters = 0.0
        var sumMillis = 0L
        for (i in 0 until n) {
            val from = route.times[i] - half
            val to = route.times[i] + half
            while (hi < n - 1 && route.times[hi + 1] <= to) {
                sumMeters += meters[hi]
                sumMillis += millis[hi]
                hi++
            }
            while (lo < hi && route.times[lo] < from) {
                sumMeters -= meters[lo]
                sumMillis -= millis[lo]
                lo++
            }
            result[i] = if (sumMillis >= MIN_WINDOW_MILLIS) sumMeters * MILLIS_PER_SECOND / sumMillis else Double.NaN
        }
        return result
    }

    private fun nearestBpm(heartRate: HeartRateSeries, time: Long): Int? {
        if (heartRate.isEmpty()) return null
        val found = heartRate.times.binarySearch(time)
        val index = if (found >= 0) {
            found
        } else {
            val after = -found - 1
            when {
                after == 0 -> 0
                after == heartRate.size -> heartRate.size - 1
                time - heartRate.times[after - 1] <= heartRate.times[after] - time -> after - 1
                else -> after
            }
        }
        return if (abs(heartRate.times[index] - time) <= MAX_HR_GAP_MILLIS) heartRate.bpm[index] else null
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        val position = p * (sorted.size - 1)
        val below = floor(position).toInt()
        val above = minOf(below + 1, sorted.size - 1)
        return sorted[below] + (sorted[above] - sorted[below]) * (position - below)
    }

    /** Heart rate further than this from a point says nothing about it. */
    const val MAX_HR_GAP_MILLIS = 15_000L

    /** Twenty seconds: smooth enough for GPS noise, short enough to show a 200 m rep. */
    const val WINDOW_MILLIS = 20_000L
    private const val MIN_WINDOW_MILLIS = 5_000L
    private const val MAX_STEP_GAP_MILLIS = 10_000L
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val MIN_MOVING_SPEED = 0.5
    private const val MIN_MOVING_POINTS = 10
    private const val LOW_PERCENTILE = 0.05
    private const val HIGH_PERCENTILE = 0.95

    /** The fastest end of the scale is at least this much faster than the slowest (15 %: 5:45 to 5:00 per km). */
    const val MIN_SPAN = 0.15
}
