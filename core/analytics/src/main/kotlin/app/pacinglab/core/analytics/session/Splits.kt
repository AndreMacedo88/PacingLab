package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.geo.haversineMeters
import app.pacinglab.core.analytics.session.SessionAnalyzer.within
import app.pacinglab.core.analytics.workout.WorkoutSummaries
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries

/** One kilometre of a run; the last split may be shorter. Times are epoch milliseconds. */
data class Split(val number: Int, val startMillis: Long, val endMillis: Long, val distanceMeters: Double, val averageHr: Int?) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * Kilometre splits from the distance covered over time: the GPS track when there is one, otherwise the
 * speed stream. When that distance comes close to the one the watch reported, it is scaled to match, so
 * the splits add up the way the watch shows them. Times between kilometre marks are interpolated
 * between samples.
 */
object Splits {
    const val SPLIT_METERS = 1_000.0

    /** A last split shorter than this is left out. */
    const val MIN_LAST_METERS = 100.0

    private const val MAX_HOLD_MILLIS = 5_000L
    private val RECONCILABLE = 0.8..1.25

    fun of(route: Route, speed: SampleSeries, heartRate: HeartRateSeries, recordedDistanceMeters: Double?): List<Split> {
        val (times, meters) = if (route.size >= 2) fromRoute(route) else fromSpeed(speed)
        if (times.size < 2) return emptyList()
        val measured = meters.last()
        if (measured < SPLIT_METERS) return emptyList()
        val scale = if (recordedDistanceMeters != null &&
            recordedDistanceMeters / measured in RECONCILABLE
        ) {
            recordedDistanceMeters / measured
        } else {
            1.0
        }
        val total = measured * scale

        fun timeAt(distance: Double): Long {
            val target = distance / scale
            var i = meters.indexOfFirst { it >= target }
            if (i <= 0) i = 1
            val span = meters[i] - meters[i - 1]
            val fraction = if (span > 0) (target - meters[i - 1]) / span else 0.0
            return times[i - 1] + ((times[i] - times[i - 1]) * fraction).toLong()
        }

        val splits = mutableListOf<Split>()
        var from = 0.0
        var start = times.first()
        while (from < total) {
            val to = minOf(from + SPLIT_METERS, total)
            if (to - from < MIN_LAST_METERS) break
            val end = if (to >= total) times.last() else timeAt(to)
            splits += Split(splits.size + 1, start, end, to - from, WorkoutSummaries.timeWeightedAverage(heartRate.within(start, end)))
            from = to
            start = end
        }
        return splits
    }

    /** Sample times and the cumulative distance along the track at each. */
    private fun fromRoute(route: Route): Pair<LongArray, DoubleArray> {
        val meters = DoubleArray(route.size)
        for (i in 1 until route.size) {
            meters[i] =
                meters[i - 1] + haversineMeters(route.latitudes[i - 1], route.longitudes[i - 1], route.latitudes[i], route.longitudes[i])
        }
        return route.times to meters
    }

    /** Cumulative distance from speed, each sample holding until the next for at most five seconds. */
    private fun fromSpeed(speed: SampleSeries): Pair<LongArray, DoubleArray> {
        val meters = DoubleArray(speed.size)
        for (i in 1 until speed.size) {
            val dt = minOf(speed.times[i] - speed.times[i - 1], MAX_HOLD_MILLIS)
            meters[i] = meters[i - 1] + speed.values[i - 1] * dt / 1000.0
        }
        return speed.times to meters
    }
}
