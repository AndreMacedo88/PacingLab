package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.geo.speeds
import app.pacinglab.core.analytics.intervals.IntervalDetection
import app.pacinglab.core.analytics.intervals.Phase
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.intervals.RecordingMode
import app.pacinglab.core.analytics.intervals.SprintDetection
import app.pacinglab.core.analytics.load.BanisterWeighting
import app.pacinglab.core.analytics.load.HeartRateLoad
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.series.secondGrid
import app.pacinglab.core.analytics.workout.SpeedCleaning
import app.pacinglab.core.analytics.workout.WorkoutSummaries
import app.pacinglab.core.analytics.zones.TimeInZones
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.ZoneSet
import kotlin.math.roundToInt

/** One recorded workout of a training session, with its streams. */
data class SessionPart(val workout: Workout, val streams: WorkoutStreams)

/**
 * One phase of an interval session with its numbers. Times are epoch milliseconds, end exclusive.
 *
 * @property distanceMeters null when the recording has no distance for this stretch.
 * @property topSpeed for reps: the fastest three seconds, m/s.
 * @property hrDrop for a recovery after a rep: how far heart rate fell from the rep's peak within the
 *   first minute of the recovery (heart rate recovery, HRR), or over the whole recovery when it is
 *   shorter, which [hrDropSeconds] gives. Null after sprints, where heart rate is still rising.
 */
data class PhaseSummary(
    val kind: PhaseKind,
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double?,
    val averageHr: Int?,
    val maxHr: Int?,
    val topSpeed: Double? = null,
    val hrDrop: Int? = null,
    val hrDropSeconds: Int? = null,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/** Phases of one kind added up, for example all reps of a session. */
data class PhaseTotals(val kind: PhaseKind, val count: Int, val durationMillis: Long, val distanceMeters: Double?, val averageHr: Int?)

/**
 * What PacingLab derives from a training session.
 *
 * @property phases reps, recoveries, warm-up and cool-down in time order; empty when the session
 *   holds no intervals.
 * @property zones time in each zone of every zone set passed to [SessionAnalyzer.analyze], in the
 *   same order.
 * @property load training load of the parts with heart rate; null without heart rate or without the
 *   resting heart rate the load models need.
 * @property sprints true when the reps are sprints ([SprintDetection]) rather than intervals.
 */
data class SessionAnalysis(
    val phases: List<PhaseSummary>,
    val zones: List<ZoneDistribution>,
    val load: LoadScores? = null,
    val sprints: Boolean = false,
) {
    val isIntervalSession: Boolean get() = phases.any { it.kind == PhaseKind.WORK }

    /** Average heart rate drop over the recoveries that have one, bpm. */
    val averageHrDrop: Int? get() = phases.mapNotNull { it.hrDrop }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    /** Totals of [kinds]; warm-up and cool-down are usually reported together. */
    fun totals(vararg kinds: PhaseKind): PhaseTotals? {
        val selected = phases.filter { it.kind in kinds }
        if (selected.isEmpty()) return null
        val distances = selected.mapNotNull { it.distanceMeters }
        val withHr = selected.filter { it.averageHr != null }
        return PhaseTotals(
            kind = kinds.first(),
            count = selected.size,
            durationMillis = selected.sumOf { it.durationMillis },
            distanceMeters = if (distances.size == selected.size) distances.sum() else null,
            // Weighted by duration, so a long rep counts more than a short one.
            averageHr = if (withHr.isEmpty()) {
                null
            } else {
                (withHr.sumOf { it.averageHr!! * it.durationMillis.toDouble() } / withHr.sumOf { it.durationMillis }).roundToInt()
            },
        )
    }
}

object SessionAnalyzer {
    /** Reported and speed-derived distance closer than this ratio are reconciled; further apart, the speed stream is kept. */
    private val RECONCILABLE = 0.8..1.25

    private const val TOP_SPEED_SECONDS = 3

    /**
     * Splits the session into reps and recoveries where its runs allow it and adds up time in zones and,
     * with [anchors], training load.
     *
     * Each run is analysed on its own, because watches record the warm-up and the intervals as separate
     * workouts; workouts before the first interval workout count as warm-up, those after the last as
     * cool-down. Phase distances come from the speed stream, scaled so that they add up to the
     * workout's recorded distance.
     */
    fun analyze(
        parts: List<SessionPart>,
        zoneSets: List<ZoneSet> = emptyList(),
        anchors: HeartRateAnchors? = null,
        weighting: BanisterWeighting = BanisterWeighting.MALE,
    ): SessionAnalysis {
        val ordered = parts.sortedBy { it.workout.start }
        val found = ordered.map(::detect)
        val sprints = found.any { it != null } && found.filterNotNull().all { it.sprints }
        val detected = ordered.mapIndexed { i, part -> found[i]?.let { summarize(part, it) } }
        val firstIntervals = detected.indexOfFirst { it != null }
        val phases = if (firstIntervals < 0) {
            emptyList()
        } else {
            val lastIntervals = detected.indexOfLast { it != null }
            ordered.indices.flatMap { i ->
                detected[i] ?: listOf(
                    wholePart(
                        ordered[i],
                        when {
                            i < firstIntervals -> PhaseKind.WARM_UP
                            i > lastIntervals -> PhaseKind.COOL_DOWN
                            else -> PhaseKind.RECOVERY
                        },
                    ),
                )
            }
        }
        val zones = zoneSets.map { set ->
            val millis = LongArray(set.size)
            for (part in ordered) {
                val distribution = TimeInZones.compute(part.streams.heartRate, set)
                for (i in millis.indices) millis[i] += distribution.millis[i]
            }
            ZoneDistribution(set, millis)
        }
        val load = anchors?.let { a ->
            ordered.filterNot { it.streams.heartRate.isEmpty() }
                .mapNotNull { HeartRateLoad.of(it.streams.heartRate, a, weighting) }
                .takeIf { it.isNotEmpty() }
                ?.fold(LoadScores.ZERO, LoadScores::plus)
        }
        return SessionAnalysis(phases, zones, load, sprints)
    }

    private class Detected(val phases: List<Phase>, val speed: SampleSeries, val sprints: Boolean)

    /**
     * Sprints where the run has them, else intervals. Sprint detection is the stricter of the two (short
     * bursts at sprint speed with rest between), while interval detection in interval mode accepts any
     * alternation and would take sprints for short reps.
     */
    private fun detect(part: SessionPart): Detected? {
        val workout = part.workout
        if (workout.sport != Sport.RUN) return null
        // Runs from before August 2026 came without a speed stream; their GPS track stands in.
        val recorded = if (part.streams.speed.size > 0) part.streams.speed else part.streams.route.speeds()
        val speed = SpeedCleaning.dropImplausible(recorded, SpeedCleaning.maxPlausibleSpeed(Sport.RUN))
        if (speed.size < 2) return null
        val start = workout.start.toEpochMilli()
        val end = workout.end.toEpochMilli()
        SprintDetection.detect(speed, start, end)?.let { return Detected(it, speed, sprints = true) }
        return IntervalDetection.detect(speed, start, end, RecordingMode.of(workout.activity))?.let { Detected(it, speed, sprints = false) }
    }

    private fun summarize(part: SessionPart, detected: Detected): List<PhaseSummary> {
        val phases = detected.phases
        val measured = phases.sumOf { it.distanceMeters }
        val recorded = part.workout.summary.distanceMeters
        val scale = if (recorded != null && measured > 0 && recorded / measured in RECONCILABLE) recorded / measured else 1.0
        val heartRate = part.streams.heartRate
        return phases.mapIndexed { i, phase ->
            val hr = heartRate.within(phase.startMillis, phase.endMillis)
            val previous = phases.getOrNull(i - 1)
            val drop = if (!detected.sprints && phase.kind == PhaseKind.RECOVERY && previous?.kind == PhaseKind.WORK) {
                HeartRateRecovery.of(heartRate, previous.endMillis, phase.endMillis)
            } else {
                null
            }
            PhaseSummary(
                kind = phase.kind,
                startMillis = phase.startMillis,
                endMillis = phase.endMillis,
                distanceMeters = phase.distanceMeters * scale,
                averageHr = WorkoutSummaries.timeWeightedAverage(hr),
                maxHr = WorkoutSummaries.spikeResistantMax(hr),
                topSpeed = if (phase.kind ==
                    PhaseKind.WORK
                ) {
                    topSpeed(detected.speed, phase.startMillis, phase.endMillis)?.let { it * scale }
                } else {
                    null
                },
                hrDrop = drop?.bpm,
                hrDropSeconds = drop?.seconds,
            )
        }
    }

    /** The fastest three consecutive seconds between [from] and [to], m/s. */
    private fun topSpeed(speed: SampleSeries, from: Long, to: Long): Double? {
        val grid = secondGrid(speed.times, speed.values, from, ((to - from) / 1000).toInt())
        var best: Double? = null
        for (k in 0..grid.size - TOP_SPEED_SECONDS) {
            var sum = 0.0
            var complete = true
            for (j in k until k + TOP_SPEED_SECONDS) {
                if (grid[j].isNaN()) complete = false else sum += grid[j]
            }
            if (complete && (best == null || sum / TOP_SPEED_SECONDS > best)) best = sum / TOP_SPEED_SECONDS
        }
        return best
    }

    private fun wholePart(part: SessionPart, kind: PhaseKind) = PhaseSummary(
        kind = kind,
        startMillis = part.workout.start.toEpochMilli(),
        endMillis = part.workout.end.toEpochMilli(),
        distanceMeters = part.workout.summary.distanceMeters,
        averageHr = part.workout.summary.averageHr,
        maxHr = part.workout.summary.maxHr,
    )

    /** Samples in [from, to). */
    internal fun HeartRateSeries.within(from: Long, to: Long): HeartRateSeries {
        val first = times.indexOfFirstAtLeast(from)
        val end = times.indexOfFirstAtLeast(to)
        return HeartRateSeries(times.copyOfRange(first, end), bpm.copyOfRange(first, end))
    }

    private fun LongArray.indexOfFirstAtLeast(value: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (this[mid] < value) low = mid + 1 else high = mid
        }
        return low
    }
}
