package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class RunningFormTest {
    private val start = 1_800_000_000_000L
    private val minute = 60_000L

    /** One step record a minute with [cadences] steps, as a running total. */
    private fun steps(cadences: List<Int>): SampleSeries {
        var total = 0.0
        val times = mutableListOf(start)
        val values = mutableListOf(0.0)
        cadences.forEachIndexed { i, c ->
            total += c
            times += start + (i + 1) * minute
            values += total
        }
        return SampleSeries(times.toLongArray(), values.toDoubleArray())
    }

    /** One speed sample a second, [speeds] m/s per minute. */
    private fun speed(speeds: List<Double>): SampleSeries {
        val times = LongArray(speeds.size * 60) { start + it * 1_000L }
        return SampleSeries(times, DoubleArray(times.size) { speeds[it / 60] })
    }

    @Test
    fun `stride is speed over cadence for each running minute`() {
        val minutes = RunningForm.minutes(steps(listOf(170, 172, 174)), speed(listOf(3.0, 3.2, 3.4)), start, start + 3 * minute)

        assertThat(minutes).hasSize(3)
        assertThat(minutes[0].cadence).isCloseTo(170.0, 1e-9)
        assertThat(minutes[0].strideMeters).isCloseTo(3.0 * 60 / 170, 1e-9)
        assertThat(RunningForm.summary(minutes)!!.cadence).isCloseTo(172.0, 1e-9)
    }

    @Test
    fun `walking and standing minutes are left out`() {
        val minutes = RunningForm.minutes(
            steps(listOf(110, 175, 0, 176, 177)),
            speed(listOf(1.4, 3.0, 0.0, 3.1, 3.1)),
            start,
            start + 5 * minute,
        )

        assertThat(minutes.map { it.cadence }).isEqualTo(listOf(175.0, 176.0, 177.0))
    }

    @Test
    fun `reps share their steps and distance`() {
        val counts = steps(listOf(160, 180, 180, 160))
        val reps = listOf(start + minute..start + 3 * minute)

        val form = RunningForm.over(counts, reps, distanceMeters = 720.0)!!

        assertThat(form.cadence).isCloseTo(180.0, 1e-9)
        assertThat(form.strideMeters).isCloseTo(2.0, 1e-9)
    }

    @Test
    fun `too few running minutes give no summary`() {
        assertThat(
            RunningForm.summary(RunningForm.minutes(steps(listOf(170, 170)), speed(listOf(3.0, 3.0)), start, start + 2 * minute)),
        ).isNull()
    }

    @Test
    fun `rep recovery is the median full-minute drop after hard reps`() {
        fun phase(kind: PhaseKind, max: Int? = null, drop: Int? = null, seconds: Int? = null) =
            PhaseSummary(kind, 0, 1, null, null, max, hrDrop = drop, hrDropSeconds = seconds)
        val analysis = SessionAnalysis(
            phases = listOf(
                phase(PhaseKind.WARM_UP),
                phase(PhaseKind.WORK, max = 175),
                phase(PhaseKind.RECOVERY, drop = 30, seconds = 60),
                phase(PhaseKind.WORK, max = 178),
                phase(PhaseKind.RECOVERY, drop = 26, seconds = 60),
                phase(PhaseKind.WORK, max = 140),
                phase(PhaseKind.RECOVERY, drop = 50, seconds = 60),
                phase(PhaseKind.WORK, max = 180),
                phase(PhaseKind.RECOVERY, drop = 20, seconds = 40),
            ),
            zones = emptyList(),
        )

        val recovery = HeartRateRecovery.ofSession(analysis, maxHr = 186)!!

        // The easy rep (140 < 85 % of 186) and the short recovery are left out.
        assertThat(recovery.reps).isEqualTo(2)
        assertThat(recovery.dropBpm).isCloseTo(28.0, 1e-9)
        assertThat(recovery.peakHr).isEqualTo(178)
    }
}
