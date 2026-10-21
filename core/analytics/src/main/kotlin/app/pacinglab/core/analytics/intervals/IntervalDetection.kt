package app.pacinglab.core.analytics.intervals

import app.pacinglab.core.model.SampleSeries
import kotlin.math.sqrt

/** What a stretch of an interval session was for. */
enum class PhaseKind {
    WARM_UP,
    WORK,
    RECOVERY,
    COOL_DOWN,
}

/**
 * A stretch of a workout with one purpose: a rep, the recovery after it, or the easy running around
 * the reps. Times are epoch milliseconds, end exclusive.
 */
data class Phase(val kind: PhaseKind, val startMillis: Long, val endMillis: Long, val distanceMeters: Double) {
    val durationMillis: Long get() = endMillis - startMillis
}

/** How the watch recorded the workout, which sets how much evidence the detector asks for. */
enum class RecordingMode {
    /** Recorded in interval mode: alternation between reps and recoveries is expected. */
    INTERVALS,

    /**
     * Recorded as a plain run. Only clearly regular alternation counts, so that stops at traffic
     * lights and pace changes on hills are not taken for reps.
     */
    CONTINUOUS,
    ;

    companion object {
        /** Activity names (Health Connect exercise types) that watches use for interval timers. */
        private val INTERVAL_ACTIVITIES = setOf("boot_camp", "high_intensity_interval_training", "interval_training")

        fun of(activity: String): RecordingMode = if (activity in INTERVAL_ACTIVITIES) INTERVALS else CONTINUOUS
    }
}

/**
 * Splits a run into reps and recoveries from its speed stream alone, so it works for interval
 * workouts that carry no GPS track and no rep markers (the Fitbit Sense records neither).
 *
 * The method:
 * 1. Speed is put on a one-second grid (each sample holds for up to [MAX_HOLD_MILLIS]) and smoothed
 *    with a running median, which removes single-sample GPS or step-sensor spikes but keeps the
 *    sharp edges of a rep.
 * 2. Otsu's method splits the smoothed speeds into a fast and a slow group; the threshold between
 *    them adapts to each session, so it works for walking recoveries and for jogged ones.
 * 3. Each second is labelled fast or slow with hysteresis around that threshold, and stretches
 *    shorter than [MIN_PHASE_SECONDS] are absorbed by their neighbours.
 * 4. Fast stretches at either end that are clearly shorter or slower than the typical rep are
 *    warm-up or cool-down running, not reps.
 * 5. The result is accepted only if reps are running and clearly faster than recoveries, and, for
 *    plain runs, the pattern is regular.
 *
 * Tuned on wrist-measured speed (1 Hz, step and GPS based) from Fitbit interval workouts: 4 and 5 minute
 * reps with walking recoveries and 1 minute jogged ones.
 */
object IntervalDetection {
    /** A speed sample stays valid for at most this long when the next one is missing. */
    const val MAX_HOLD_MILLIS = 5_000L

    /** Below this share of seconds with a speed value, the stream is too sparse to analyse. */
    const val MIN_COVERAGE = 0.7

    /** Shortest rep or recovery; anything shorter is noise around a transition. */
    const val MIN_PHASE_SECONDS = 10

    /** Reps must be at least this much faster, on average, than the recoveries between them. */
    const val MIN_SPEED_RATIO = 1.35

    /** Slowest average rep speed, m/s (about 11:00/km): gym circuits timed in interval mode stay below it. */
    const val MIN_REP_SPEED = 1.5

    private const val MEDIAN_HALF_WINDOW = 3
    private const val HYSTERESIS_SHARE = 0.15
    private const val EDGE_SHORT_SHARE = 0.5
    private const val EDGE_SLOW_FACTOR = 1.10

    // Extra evidence for plain runs.
    private const val CONTINUOUS_MIN_REPS = 3
    private const val CONTINUOUS_MAX_DURATION_CV = 0.25
    private const val CONTINUOUS_MAX_DISTANCE_CV = 0.15
    private const val CONTINUOUS_MAX_RECOVERY_CV = 0.5
    private const val CONTINUOUS_MIN_RECOVERY_SHARE = 0.05

    /**
     * Returns the phases covering [startMillis, endMillis), or null when the run is not an interval
     * session or the speed stream is too sparse. [speed] must be in m/s with implausible values
     * already removed.
     */
    fun detect(speed: SampleSeries, startMillis: Long, endMillis: Long, mode: RecordingMode): List<Phase>? {
        val grid = grid(speed, startMillis, endMillis) ?: return null
        val smoothed = runningMedian(grid, MEDIAN_HALF_WINDOW)
        val split = otsu(smoothed.filter { it.isFinite() }.toDoubleArray()) ?: return null
        val labels = hysteresis(smoothed, split)
        val runs = absorbShortRuns(toRuns(labels))
        val phases = classify(runs, grid) ?: return null
        if (!accepted(phases, mode)) return null
        return phases.map { it.toPhase(startMillis) }
    }

    /** Speed per second; NaN where no sample is close enough. Null when coverage is too low. */
    internal fun grid(speed: SampleSeries, startMillis: Long, endMillis: Long): DoubleArray? {
        val seconds = ((endMillis - startMillis) / 1000).toInt()
        if (seconds <= 0) return null
        val out = DoubleArray(seconds) { Double.NaN }
        var j = -1
        for (k in 0 until seconds) {
            val t = startMillis + k * 1000L
            while (j + 1 < speed.size && speed.times[j + 1] <= t) j++
            if (j >= 0 && t - speed.times[j] <= MAX_HOLD_MILLIS) out[k] = speed.values[j]
        }
        val covered = out.count { it.isFinite() }
        return if (covered >= MIN_COVERAGE * seconds) out else null
    }

    internal fun runningMedian(values: DoubleArray, halfWindow: Int): DoubleArray {
        val window = DoubleArray(2 * halfWindow + 1)
        return DoubleArray(values.size) { i ->
            var n = 0
            for (k in maxOf(0, i - halfWindow)..minOf(values.lastIndex, i + halfWindow)) {
                if (values[k].isFinite()) window[n++] = values[k]
            }
            if (n == 0) {
                Double.NaN
            } else {
                window.sort(0, n)
                if (n % 2 == 1) window[n / 2] else (window[n / 2 - 1] + window[n / 2]) / 2
            }
        }
    }

    /** Threshold between two groups of values that maximises the between-group variance. */
    internal data class Split(val threshold: Double, val lowMean: Double, val highMean: Double)

    internal fun otsu(values: DoubleArray): Split? {
        if (values.size < 2) return null
        val sorted = values.sortedArray()
        val n = sorted.size
        val total = sorted.sum()
        var below = 0.0
        var best: Split? = null
        var bestVariance = 0.0
        for (i in 1 until n) {
            below += sorted[i - 1]
            if (sorted[i] == sorted[i - 1]) continue
            val weight = i.toDouble() / n
            val lowMean = below / i
            val highMean = (total - below) / (n - i)
            val variance = weight * (1 - weight) * (highMean - lowMean) * (highMean - lowMean)
            if (variance > bestVariance) {
                bestVariance = variance
                best = Split((sorted[i - 1] + sorted[i]) / 2, lowMean, highMean)
            }
        }
        return best
    }

    /** True for fast seconds. Missing values keep the current state. */
    private fun hysteresis(smoothed: DoubleArray, split: Split): BooleanArray {
        val band = HYSTERESIS_SHARE * (split.highMean - split.lowMean)
        val first = smoothed.firstOrNull { it.isFinite() } ?: split.lowMean
        var fast = first >= split.threshold
        return BooleanArray(smoothed.size) { i ->
            val v = smoothed[i]
            if (v.isFinite()) {
                if (!fast && v >= split.threshold + band) fast = true
                if (fast && v <= split.threshold - band) fast = false
            }
            fast
        }
    }

    /** A stretch of seconds [start, end) with the same label. */
    private data class Run(val fast: Boolean, val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    private fun toRuns(labels: BooleanArray): MutableList<Run> {
        val runs = mutableListOf<Run>()
        var start = 0
        for (i in 1..labels.size) {
            if (i == labels.size || labels[i] != labels[start]) {
                runs += Run(labels[start], start, i)
                start = i
            }
        }
        return runs
    }

    /** Repeatedly folds the shortest too-short run into its neighbours until none is left. */
    private fun absorbShortRuns(runs: MutableList<Run>): List<Run> {
        while (runs.size > 1) {
            val index = runs.indices.filter { runs[it].length < MIN_PHASE_SECONDS }.minByOrNull { runs[it].length } ?: break
            when (index) {
                0 -> runs[1] = runs[1].copy(start = runs[0].start)

                runs.lastIndex -> runs[index - 1] = runs[index - 1].copy(end = runs[index].end)

                else -> {
                    runs[index - 1] = runs[index - 1].copy(end = runs[index + 1].end)
                    runs.removeAt(index + 1)
                }
            }
            runs.removeAt(index)
        }
        return runs
    }

    private data class Stretch(val kind: PhaseKind, val start: Int, val end: Int, val meters: Double) {
        val seconds: Int get() = end - start
        val speed: Double get() = meters / seconds

        fun toPhase(origin: Long) = Phase(kind, origin + start * 1000L, origin + end * 1000L, meters)
    }

    private fun classify(runs: List<Run>, grid: DoubleArray): List<Stretch>? {
        fun meters(run: Run): Double {
            var sum = 0.0
            for (k in run.start until run.end) if (grid[k].isFinite()) sum += grid[k]
            return sum
        }
        val reps = runs.indices.filter { runs[it].fast }.toMutableList()
        while (reps.size > 2) {
            val durations = reps.map { runs[it].length.toDouble() }
            val paces = reps.map { runs[it].length / meters(runs[it]) }
            val typicalDuration = median(durations)
            val typicalPace = median(paces)
            fun unlikeRep(index: Int): Boolean {
                val run = runs[index]
                return run.length < EDGE_SHORT_SHARE * typicalDuration || run.length / meters(run) > EDGE_SLOW_FACTOR * typicalPace
            }
            when {
                unlikeRep(reps.first()) -> reps.removeAt(0)
                unlikeRep(reps.last()) -> reps.removeAt(reps.lastIndex)
                else -> break
            }
        }
        if (reps.size < 2) return null
        val first = reps.first()
        val last = reps.last()
        val stretches = mutableListOf<Stretch>()
        if (first > 0) stretches += Stretch(PhaseKind.WARM_UP, 0, runs[first - 1].end, runs.subList(0, first).sumOf(::meters))
        for (i in first..last) {
            val run = runs[i]
            stretches += Stretch(if (run.fast) PhaseKind.WORK else PhaseKind.RECOVERY, run.start, run.end, meters(run))
        }
        if (last < runs.lastIndex) {
            stretches +=
                Stretch(PhaseKind.COOL_DOWN, runs[last + 1].start, runs.last().end, runs.subList(last + 1, runs.size).sumOf(::meters))
        }
        return stretches
    }

    private fun accepted(phases: List<Stretch>, mode: RecordingMode): Boolean {
        val reps = phases.filter { it.kind == PhaseKind.WORK }
        val recoveries = phases.filter { it.kind == PhaseKind.RECOVERY }
        if (reps.size < 2 || recoveries.isEmpty()) return false
        val repSpeed = reps.sumOf { it.meters } / reps.sumOf { it.seconds }
        val recoverySpeed = recoveries.sumOf { it.meters } / recoveries.sumOf { it.seconds }
        if (repSpeed < MIN_REP_SPEED || repSpeed < MIN_SPEED_RATIO * recoverySpeed) return false
        if (mode == RecordingMode.INTERVALS) return true

        val regularReps = coefficientOfVariation(reps.map { it.seconds.toDouble() }) <= CONTINUOUS_MAX_DURATION_CV ||
            coefficientOfVariation(reps.map { it.meters }) <= CONTINUOUS_MAX_DISTANCE_CV
        val regularRecoveries = coefficientOfVariation(recoveries.map { it.seconds.toDouble() }) <= CONTINUOUS_MAX_RECOVERY_CV
        val span = phases.last { it.kind == PhaseKind.WORK }.end - phases.first { it.kind == PhaseKind.WORK }.start
        val recoveryShare = recoveries.sumOf { it.seconds }.toDouble() / span
        return reps.size >= CONTINUOUS_MIN_REPS && regularReps && regularRecoveries && recoveryShare >= CONTINUOUS_MIN_RECOVERY_SHARE
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun coefficientOfVariation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (mean == 0.0) return Double.POSITIVE_INFINITY
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance) / mean
    }
}
