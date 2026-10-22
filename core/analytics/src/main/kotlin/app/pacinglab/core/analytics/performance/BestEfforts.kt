package app.pacinglab.core.analytics.performance

import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import java.time.LocalDate

/** Where the speed of an effort came from. */
enum class EffortSource {
    /** The GPS track. */
    GPS,

    /**
     * The watch's own speed, from steps and stride length, in workouts recorded without GPS (the Sense
     * records none in interval mode). It cannot be checked against anything, and at the same heart rate
     * it read about 14 % faster than GPS in the owner's data, so efforts from it are estimates.
     */
    WATCH,
}

/** Standard distances for best efforts and race predictions. */
enum class RaceDistance(val meters: Double) {
    M400(400.0),
    K1(1_000.0),
    K3(3_000.0),
    K5(5_000.0),
    K10(10_000.0),
    HALF_MARATHON(21_097.5),
    MARATHON(42_195.0),
}

/** The fastest stretch of one run over a distance or a duration. */
data class Effort(
    val workoutId: String,
    val date: LocalDate,
    val source: EffortSource,
    val startMillis: Long,
    val seconds: Double,
    val meters: Double,
) {
    /** Average speed, m/s. */
    val speed: Double get() = meters / seconds
}

/**
 * A run's best efforts.
 *
 * @property distances the fastest time over each distance the run covered.
 * @property durations the farthest distance in each of [BestEfforts.DURATIONS] seconds.
 * @property sustained the farthest distance in each of [BestEfforts.SUSTAINED_DURATIONS] seconds run
 *   without slowing to recover and hard enough to hold heart rate at threshold: the all-out efforts
 *   critical speed needs. A window that spans a rep and its recovery is a best duration but not sustained.
 */
data class RunEfforts(val distances: Map<RaceDistance, Effort>, val durations: Map<Int, Effort>, val sustained: Map<Int, Effort>) {
    companion object {
        val EMPTY = RunEfforts(emptyMap(), emptyMap(), emptyMap())
    }
}

/** The best effort of each source for one distance or duration. */
data class BestPair(val gps: Effort?, val watch: Effort?) {
    /** The faster of the two, GPS on a tie. */
    val best: Effort? get() = listOfNotNull(gps, watch).maxByOrNull { it.speed }
}

/** Best efforts over many runs. */
data class EffortBests(val distances: Map<RaceDistance, BestPair>, val durations: Map<Int, BestPair>) {
    fun isEmpty(): Boolean = distances.isEmpty() && durations.isEmpty()

    companion object {
        val EMPTY = EffortBests(emptyMap(), emptyMap())
    }
}

/**
 * Best efforts and the speed-duration curve, found in every run rather than in races only.
 *
 * Speed holds from one sample to the next for at most five seconds, so a gap in the recording counts as
 * standing still. A stretch counts only if heart rate rose to at least [MIN_PEAK_SHARE] of maximum
 * by half a minute after it: in the owner's data a fast "kilometre" with heart rate at 120-128 was a
 * ride in a vehicle after the run, while every real fast kilometre reached 143 bpm or more.
 */
object BestEfforts {
    /** Durations of the speed-duration curve, seconds. */
    val DURATIONS = listOf(60, 120, 180, 300, 420, 600, 720, 900, 1200, 1800, 2700, 3600, 5400, 7200)

    /** Durations for critical speed: 3, 7 and 12 minutes, as in Smyth and Muniz-Pumares (2020). */
    val SUSTAINED_DURATIONS = listOf(180, 420, 720)

    const val MIN_PEAK_SHARE = 0.75

    /** Seconds after a stretch in which heart rate may still catch up with it. */
    private const val HEART_RATE_LAG = 30

    /** A sustained effort has no half minute slower than this share of its average. */
    private const val SUSTAINED_SHARE = 0.8
    private const val CHUNK = 30
    private const val SHORT_EFFORT_SECONDS = 300
    private const val SHORT_EFFORT_HR_SHARE = 0.95

    fun of(
        workoutId: String,
        date: LocalDate,
        source: EffortSource,
        speed: SampleSeries,
        heartRate: HeartRateSeries,
        startMillis: Long,
        endMillis: Long,
        anchors: HeartRateAnchors?,
    ): RunEfforts {
        val seconds = ((endMillis - startMillis) / 1000).toInt()
        if (seconds <= 0 || speed.isEmpty()) return RunEfforts.EMPTY
        val grid = secondGrid(speed.times, speed.values, startMillis, seconds)
        val distance = DoubleArray(seconds + 1)
        for (s in 0 until seconds) distance[s + 1] = distance[s] + grid[s].takeIf { it.isFinite() && it > 0 }.orZero()
        val hrGrid = secondGrid(heartRate.times, heartRate.bpm.map { it.toDouble() }.toDoubleArray(), startMillis, seconds + HEART_RATE_LAG)
        val hr = RangeMax(hrGrid)
        val minPeak = anchors?.let { MIN_PEAK_SHARE * it.maxHr }
        fun plausible(from: Int, until: Int): Boolean =
            minPeak == null || hr.max(from, until + HEART_RATE_LAG)?.let { it >= minPeak } ?: true
        fun effort(from: Int, length: Double, meters: Double) = Effort(workoutId, date, source, startMillis + from * 1_000L, length, meters)

        val distances = mutableMapOf<RaceDistance, Effort>()
        for (target in RaceDistance.entries) {
            if (distance[seconds] < target.meters) continue
            var best: Effort? = null
            var end = 0
            for (start in 0 until seconds) {
                while (end <= seconds && distance[end] - distance[start] < target.meters) end++
                if (end > seconds) break
                // The target is reached during second end - 1; its speed was distance[end] - distance[end - 1].
                val before = distance[end - 1] - distance[start]
                val time = (end - 1 - start) + (target.meters - before) / (distance[end] - distance[end - 1])
                if ((best == null || time < best.seconds) && plausible(start, end)) best = effort(start, time, target.meters)
            }
            best?.let { distances[target] = it }
        }

        val durations = mutableMapOf<Int, Effort>()
        val sustained = mutableMapOf<Int, Effort>()
        for (length in DURATIONS) {
            if (length > seconds) break
            var best: Effort? = null
            var hardest: Effort? = null
            for (start in 0..seconds - length) {
                val meters = distance[start + length] - distance[start]
                if (meters <= 0) continue
                if ((best == null || meters > best.meters) &&
                    plausible(start, start + length)
                ) {
                    best = effort(start, length.toDouble(), meters)
                }
                if (length in SUSTAINED_DURATIONS && anchors != null && (hardest == null || meters > hardest.meters) &&
                    steady(distance, start, length, meters) && hard(hrGrid, start, length, anchors.thresholdHr)
                ) {
                    hardest = effort(start, length.toDouble(), meters)
                }
            }
            best?.let { durations[length] = it }
            hardest?.let { sustained[length] = it }
        }
        return RunEfforts(distances, durations, sustained)
    }

    /** The best GPS and the best watch effort for every distance and duration over [runs]. */
    fun best(runs: List<RunEfforts>): EffortBests {
        fun pair(efforts: List<Effort>, faster: (Effort) -> Double) = BestPair(
            gps = efforts.filter { it.source == EffortSource.GPS }.maxByOrNull(faster),
            watch = efforts.filter { it.source == EffortSource.WATCH }.maxByOrNull(faster),
        )
        val distances = RaceDistance.entries.mapNotNull { target ->
            val efforts = runs.mapNotNull { it.distances[target] }
            if (efforts.isEmpty()) null else target to pair(efforts) { it.speed }
        }.toMap()
        val durations = DURATIONS.mapNotNull { length ->
            val efforts = runs.mapNotNull { it.durations[length] }
            if (efforts.isEmpty()) null else length to pair(efforts) { it.meters }
        }.toMap()
        return EffortBests(distances, durations)
    }

    /**
     * Mean heart rate over the second half of the window at threshold or above: an all-out effort of 3 to
     * 12 minutes runs above critical speed and drives heart rate past threshold. Heart rate lags, so a
     * 3-minute effort needs only [SHORT_EFFORT_HR_SHARE] of threshold. A single beat above threshold in an
     * easy run is not enough.
     */
    private fun hard(hr: DoubleArray, start: Int, length: Int, thresholdHr: Int): Boolean {
        val values = (start + length / 2 until start + length).map { hr[it] }.filter { it.isFinite() }
        if (values.size < length / 4) return false
        val share = if (length < SHORT_EFFORT_SECONDS) SHORT_EFFORT_HR_SHARE else 1.0
        return values.average() >= share * thresholdHr
    }

    /** No half minute of the window slower than [SUSTAINED_SHARE] of its average speed. */
    private fun steady(distance: DoubleArray, start: Int, length: Int, meters: Double): Boolean {
        val floor = SUSTAINED_SHARE * meters / length * CHUNK
        var chunk = start
        while (chunk + CHUNK <= start + length) {
            if (distance[chunk + CHUNK] - distance[chunk] < floor) return false
            chunk += CHUNK
        }
        return true
    }

    /** Maximum of any range of [values] in constant time (a sparse table); NaN values are ignored. */
    private class RangeMax(values: DoubleArray) {
        private val size = values.size
        private val levels: List<DoubleArray>

        init {
            val table = mutableListOf(DoubleArray(size) { if (values[it].isFinite()) values[it] else Double.NEGATIVE_INFINITY })
            var span = 1
            while (span * 2 <= size) {
                val previous = table.last()
                table += DoubleArray(size - span * 2 + 1) { maxOf(previous[it], previous[it + span]) }
                span *= 2
            }
            levels = table
        }

        /** Highest value in [from] until [until], or null when the range holds no value. */
        fun max(from: Int, until: Int): Double? {
            val end = minOf(until, size)
            if (from >= end) return null
            val level = 31 - Integer.numberOfLeadingZeros(end - from)
            val max = maxOf(levels[level][from], levels[level][end - (1 shl level)])
            return max.takeIf { it.isFinite() }
        }
    }

    private fun Double?.orZero(): Double = this ?: 0.0
}
