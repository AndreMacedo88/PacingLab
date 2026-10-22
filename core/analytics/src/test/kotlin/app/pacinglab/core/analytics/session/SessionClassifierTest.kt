package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class SessionClassifierTest {
    // LTHR 167: Z3 from 150, Z4 from 159, Z5 from 172.
    private val zones = Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167))

    /** Reps of [repSeconds] with [recoverySeconds] between; heart rate [repHr] during reps after a 20-s climb from 140. */
    private fun intervals(reps: Int, repSeconds: Int, recoverySeconds: Int, repHr: Int): Pair<SessionAnalysis, HeartRateSeries> {
        val phases = mutableListOf<PhaseSummary>()
        val times = ArrayList<Long>()
        val bpm = ArrayList<Int>()
        var t = 0L
        repeat(reps) { i ->
            phases += PhaseSummary(PhaseKind.WORK, t, t + repSeconds * 1_000L, 1_000.0, repHr, repHr)
            for (s in 0 until repSeconds) {
                times += t + s * 1_000L
                bpm += if (s < 20) 140 + (repHr - 140) * s / 20 else repHr
            }
            t += repSeconds * 1_000L
            if (i < reps - 1) {
                phases += PhaseSummary(PhaseKind.RECOVERY, t, t + recoverySeconds * 1_000L, 100.0, 140, 150)
                for (s in 0 until recoverySeconds) {
                    times += t + s * 1_000L
                    bpm += 140
                }
                t += recoverySeconds * 1_000L
            }
        }
        return SessionAnalysis(phases, emptyList()) to HeartRateSeries(times.toLongArray(), bpm.toIntArray())
    }

    private fun steady(seconds: Int, hr: (Int) -> Int) = HeartRateSeries(LongArray(seconds) { it * 1_000L }, IntArray(seconds) { hr(it) })

    private fun classifyIntervals(reps: Int, repSeconds: Int, recoverySeconds: Int, repHr: Int): SessionKind {
        val (analysis, hr) = intervals(reps, repSeconds, recoverySeconds, repHr)
        return SessionClassifier.classify(analysis, hr, zones, movingMillis = hr.times.last())
    }

    @Test
    fun `intervals are named by where heart rate sat during the reps`() {
        assertThat(
            classifyIntervals(reps = 5, repSeconds = 240, recoverySeconds = 120, repHr = 176),
        ).isEqualTo(SessionKind.VO2MAX_INTERVALS)
        assertThat(
            classifyIntervals(reps = 3, repSeconds = 600, recoverySeconds = 90, repHr = 165),
        ).isEqualTo(SessionKind.THRESHOLD_INTERVALS)
        assertThat(classifyIntervals(reps = 4, repSeconds = 480, recoverySeconds = 60, repHr = 154)).isEqualTo(SessionKind.TEMPO_INTERVALS)
        assertThat(classifyIntervals(reps = 6, repSeconds = 300, recoverySeconds = 60, repHr = 138)).isEqualTo(SessionKind.INTERVALS)
    }

    @Test
    fun `short reps stay plain intervals unless heart rate reached Z5`() {
        assertThat(classifyIntervals(reps = 10, repSeconds = 60, recoverySeconds = 60, repHr = 166)).isEqualTo(SessionKind.INTERVALS)
        assertThat(classifyIntervals(reps = 10, repSeconds = 60, recoverySeconds = 60, repHr = 178)).isEqualTo(SessionKind.VO2MAX_INTERVALS)
    }

    @Test
    fun `sprints are sprints whatever the heart rate`() {
        val (analysis, hr) = intervals(reps = 5, repSeconds = 12, recoverySeconds = 50, repHr = 120)
        assertThat(
            SessionClassifier.classify(analysis.copy(sprints = true), hr, zones, movingMillis = 300_000),
        ).isEqualTo(SessionKind.SPRINTS)
    }

    @Test
    fun `continuous runs are named by time in zones after the first ten minutes`() {
        val none = SessionAnalysis(emptyList(), emptyList())
        // Ten easy minutes, then 30 minutes at 166.
        val threshold = steady(40 * 60) { if (it < 600) 140 else 166 }
        assertThat(SessionClassifier.classify(none, threshold, zones, 40 * 60_000L)).isEqualTo(SessionKind.THRESHOLD_RUN)
        val tempo = steady(45 * 60) { if (it < 600) 140 else 154 }
        assertThat(SessionClassifier.classify(none, tempo, zones, 45 * 60_000L)).isEqualTo(SessionKind.TEMPO_RUN)
        val easy = steady(45 * 60) { 138 }
        assertThat(SessionClassifier.classify(none, easy, zones, 45 * 60_000L)).isEqualTo(SessionKind.EASY_RUN)
        val long = steady(95 * 60) { 142 }
        assertThat(SessionClassifier.classify(none, long, zones, 95 * 60_000L)).isEqualTo(SessionKind.LONG_RUN)
        // Hard stretches with drops to Z1 in between: not a steady run, whatever the share above Z3.
        val unsteady = steady(40 * 60) { if (it < 600 || (it / 240) % 2 == 0) 128 else 168 }
        assertThat(SessionClassifier.classify(none, unsteady, zones, 40 * 60_000L)).isEqualTo(SessionKind.RUN)
        // A short jog says nothing; without zones neither does anything else.
        assertThat(SessionClassifier.classify(none, steady(6 * 60) { 140 }, zones, 6 * 60_000L)).isEqualTo(SessionKind.RUN)
        assertThat(SessionClassifier.classify(none, threshold, null, 40 * 60_000L)).isEqualTo(SessionKind.RUN)
    }
}
