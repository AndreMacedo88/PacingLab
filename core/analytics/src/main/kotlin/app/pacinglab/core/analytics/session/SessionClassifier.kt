package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.session.SessionAnalyzer.within
import app.pacinglab.core.analytics.zones.TimeInZones
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.ZoneSet

/**
 * Names a running session by what its reps or its heart rate say it was for, on the five LTHR zones
 * (Z4 threshold, Z5 VO2max):
 *
 * - Sprints when sprint detection found them.
 * - Intervals by where heart rate sat during the reps, leaving out the first [REP_SETTLE_SECONDS] of
 *   each rep while wrist heart rate catches up: mostly Z5 is VO2max work, Z4 and above threshold work,
 *   Z3 and above tempo work. Reps shorter than [MIN_REP_SECONDS] are too short for heart rate to show
 *   their intensity and stay plain intervals unless it reached Z5 anyway.
 * - Continuous runs by time in zones after the first [WARM_UP_SECONDS]: at least [MIN_SUSTAINED_SECONDS]
 *   and half of the run in Z4 and above is a threshold run, in Z3 and above a tempo run, as long as no
 *   more than [MAX_RECOVERY_SHARE] of it sat in Z1 (a run that keeps dropping to Z1 was not steady, it
 *   had reps the speed did not show); otherwise a run of at least [LONG_RUN_SECONDS] is a long run and
 *   one mostly in Z1 and Z2 an easy run.
 */
object SessionClassifier {
    const val REP_SETTLE_SECONDS = 30
    const val MIN_REP_SECONDS = 90
    const val WARM_UP_SECONDS = 10 * 60
    const val MIN_SUSTAINED_SECONDS = 15 * 60
    const val LONG_RUN_SECONDS = 75 * 60
    const val MIN_RUN_SECONDS = 10 * 60

    private const val VO2MAX_SHARE = 0.4
    private const val MAJORITY = 0.5
    private const val EASY_SHARE = 0.7
    private const val MAX_RECOVERY_SHARE = 0.25

    /**
     * The kind of a running session from its [analysis] and its [heartRate] (the parts one after
     * another), with the athlete's five LTHR [zones]; without zones, only sprints and intervals can be
     * told apart from plain runs.
     */
    fun classify(analysis: SessionAnalysis, heartRate: HeartRateSeries, zones: ZoneSet?, movingMillis: Long): SessionKind {
        if (analysis.sprints) return SessionKind.SPRINTS
        if (analysis.isIntervalSession) return intervals(analysis, heartRate, zones)
        if (zones == null || heartRate.size < 2) return SessionKind.RUN
        val start = heartRate.times.first() + WARM_UP_SECONDS * 1_000L
        val settled = heartRate.within(start, heartRate.times.last() + 1)
        val shares = shares(listOf(settled), zones)
        val long = movingMillis >= LONG_RUN_SECONDS * 1_000L
        return when {
            shares == null -> if (long) SessionKind.LONG_RUN else SessionKind.RUN
            shares.sustained(from = 3) && shares.below(1) <= MAX_RECOVERY_SHARE -> SessionKind.THRESHOLD_RUN
            shares.sustained(from = 2) && shares.below(1) <= MAX_RECOVERY_SHARE -> SessionKind.TEMPO_RUN
            long -> SessionKind.LONG_RUN
            movingMillis >= MIN_RUN_SECONDS * 1_000L && 1 - shares.from(2) >= EASY_SHARE -> SessionKind.EASY_RUN
            else -> SessionKind.RUN
        }
    }

    private fun intervals(analysis: SessionAnalysis, heartRate: HeartRateSeries, zones: ZoneSet?): SessionKind {
        if (zones == null) return SessionKind.INTERVALS
        val reps = analysis.phases.filter { it.kind == PhaseKind.WORK }
        val settled = reps.map { heartRate.within(it.startMillis + REP_SETTLE_SECONDS * 1_000L, it.endMillis) }
        val shares = shares(settled, zones) ?: return SessionKind.INTERVALS
        val typicalRep = reps.map { it.durationMillis }.sorted()[reps.size / 2] / 1_000
        return when {
            shares.from(4) >= VO2MAX_SHARE -> SessionKind.VO2MAX_INTERVALS
            typicalRep < MIN_REP_SECONDS -> SessionKind.INTERVALS
            shares.from(3) >= MAJORITY -> SessionKind.THRESHOLD_INTERVALS
            shares.from(2) >= MAJORITY -> SessionKind.TEMPO_INTERVALS
            else -> SessionKind.INTERVALS
        }
    }

    /** Time per zone, in milliseconds. */
    private class Shares(private val millis: LongArray) {
        private val total = millis.sum()

        /** Share of the time from zone index [index] up. */
        fun from(index: Int): Double = millis.drop(index).sum().toDouble() / total

        /** Share of the time below zone index [index]. */
        fun below(index: Int): Double = 1 - from(index)

        /** Half of the time and at least [MIN_SUSTAINED_SECONDS] from zone index [from] up. */
        fun sustained(from: Int): Boolean = from(from) >= MAJORITY && millis.drop(from).sum() >= MIN_SUSTAINED_SECONDS * 1_000L
    }

    /** Time per zone over [series], or null without any. */
    private fun shares(series: List<HeartRateSeries>, zones: ZoneSet): Shares? {
        val millis = LongArray(zones.size)
        for (s in series) {
            val distribution = TimeInZones.compute(s, zones)
            for (i in millis.indices) millis[i] += distribution.millis[i]
        }
        return if (millis.sum() == 0L) null else Shares(millis)
    }
}
