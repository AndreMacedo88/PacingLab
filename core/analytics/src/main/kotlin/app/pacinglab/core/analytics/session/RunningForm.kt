package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.model.SampleSeries

/** Cadence and stride over one minute of running. */
data class FormMinute(val startMillis: Long, val cadence: Double, val strideMeters: Double, val speed: Double)

/**
 * Cadence (steps per minute, both feet) and stride length (metres per step) of running.
 *
 * @property minutes the running minutes it rests on.
 */
data class FormSummary(val cadence: Double, val strideMeters: Double, val minutes: Int)

/**
 * Cadence and stride length from the watch's step counts. Fitbit writes one step record a minute during
 * a session (no cadence records), so values are per minute: fine for steady running, coarse for reps
 * shorter than two or three minutes. Stride length is speed over cadence. Garmin and Coros measure both
 * per stride with a foot or wrist accelerometer; the minute averages here agree with such values on
 * steady running but smooth over short changes.
 *
 * [steps] holds the cumulative step count: between two points the steps are spread evenly, and a flat
 * stretch is a time without records.
 */
object RunningForm {
    /** Minutes slower than this (8 km/h) or with fewer steps are not running. */
    const val MIN_SPEED = 2.2
    const val MIN_CADENCE = 130.0

    /** Cadences above this are counting errors. */
    private const val MAX_CADENCE = 240.0

    /** Summaries rest on at least this many running minutes. */
    const val MIN_MINUTES = 3

    private const val MINUTE = 60_000L

    /** Each whole running minute of [from, to): steps from [steps], speed in m/s from [speed]. */
    fun minutes(steps: SampleSeries, speed: SampleSeries, from: Long, to: Long): List<FormMinute> {
        if (steps.size < 2 || speed.isEmpty()) return emptyList()
        val count = ((to - from) / MINUTE).toInt()
        if (count <= 0) return emptyList()
        val grid = secondGrid(speed.times, speed.values, from, (count * MINUTE / 1_000).toInt())
        return (0 until count).mapNotNull { i ->
            val start = from + i * MINUTE
            val cadence = stepsBetween(steps, start, start + MINUTE)
            val seconds = grid.copyOfRange(i * 60, (i + 1) * 60).filter { !it.isNaN() }
            if (seconds.size < 30) return@mapNotNull null
            val meanSpeed = seconds.average()
            if (meanSpeed < MIN_SPEED || cadence < MIN_CADENCE || cadence > MAX_CADENCE) return@mapNotNull null
            FormMinute(start, cadence, meanSpeed * 60 / cadence, meanSpeed)
        }
    }

    /** Medians of [minutes]; null with fewer than [MIN_MINUTES]. */
    fun summary(minutes: List<FormMinute>): FormSummary? {
        if (minutes.size < MIN_MINUTES) return null
        return FormSummary(median(minutes.map { it.cadence }), median(minutes.map { it.strideMeters }), minutes.size)
    }

    /**
     * Cadence and stride over several spans together, such as all the reps of a session: total steps over
     * total time and total distance over total steps. Spans shorter than a record are blurred with what
     * came before and after. Null when the spans hold too few steps to be running.
     */
    fun over(steps: SampleSeries, spans: List<LongRange>, distanceMeters: Double): FormSummary? {
        if (steps.size < 2 || spans.isEmpty()) return null
        return fromTotals(spans.sumOf { stepsBetween(steps, it.first, it.last) }, spans.sumOf { it.last - it.first }, distanceMeters)
    }

    /** Cadence and stride from totals: [steps] over [millis], [meters] over [steps]; null unless that is running. */
    fun fromTotals(steps: Double, millis: Long, meters: Double): FormSummary? {
        if (millis <= 0 || steps <= 0 || meters <= 0) return null
        val cadence = steps / (millis.toDouble() / MINUTE)
        if (cadence < MIN_CADENCE || cadence > MAX_CADENCE) return null
        return FormSummary(cadence, meters / steps, (millis / MINUTE).toInt())
    }

    /** Steps taken in [from, to], from the cumulative count. */
    fun stepsBetween(steps: SampleSeries, from: Long, to: Long): Double = cumulativeAt(steps, to) - cumulativeAt(steps, from)

    private fun cumulativeAt(steps: SampleSeries, time: Long): Double {
        val times = steps.times
        if (time <= times.first()) return steps.values.first()
        if (time >= times.last()) return steps.values.last()
        var i = times.binarySearch(time)
        if (i >= 0) return steps.values[i]
        i = -i - 1
        val t0 = times[i - 1]
        val t1 = times[i]
        val v0 = steps.values[i - 1]
        val v1 = steps.values[i]
        return v0 + (v1 - v0) * (time - t0) / (t1 - t0)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
