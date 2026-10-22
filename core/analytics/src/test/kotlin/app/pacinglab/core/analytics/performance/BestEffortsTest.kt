package app.pacinglab.core.analytics.performance

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BestEffortsTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val anchors = HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 50)

    /** A 1 Hz run from (seconds, m/s, bpm) blocks. */
    private fun run(vararg blocks: Triple<Int, Double, Int>): Triple<SampleSeries, HeartRateSeries, Long> {
        val seconds = blocks.sumOf { it.first }
        val speed = blocks.flatMap { (n, v, _) -> List(n) { v } }.toDoubleArray()
        val bpm = blocks.flatMap { (n, _, hr) -> List(n) { hr } }.toIntArray()
        val times = LongArray(seconds) { it * 1_000L }
        return Triple(SampleSeries(times, speed), HeartRateSeries(times, bpm), seconds * 1_000L)
    }

    private fun efforts(run: Triple<SampleSeries, HeartRateSeries, Long>, source: EffortSource = EffortSource.GPS) =
        BestEfforts.of("w", day, source, run.first, run.second, 0, run.third, anchors)

    /** Warm-up, 3 hard minutes, 2 easy, then 12 minutes at a steady hard pace. */
    private val session = run(
        Triple(600, 3.0, 145),
        Triple(180, 5.0, 175),
        Triple(120, 2.0, 160),
        Triple(720, 4.0, 172),
    )

    @Test
    fun `distances are covered in the least time, wherever they start`() {
        val efforts = efforts(session)

        assertThat(efforts.distances.getValue(RaceDistance.M400).seconds).isCloseTo(80.0, 1e-9)
        // 900 m in the hard block plus 100 m of warm-up at 3 m/s beats 100 m of the recovery at 2 m/s.
        assertThat(efforts.distances.getValue(RaceDistance.K1).seconds).isCloseTo(213.33, 0.5)
        assertThat(efforts.distances[RaceDistance.K10]).isNull()
    }

    @Test
    fun `durations give the farthest distance, and sustained ones only steady hard running`() {
        val efforts = efforts(session)

        assertThat(efforts.durations.getValue(180).meters).isCloseTo(900.0, 1e-9)
        assertThat(efforts.durations.getValue(720).meters).isCloseTo(2_880.0, 1e-9)
        assertThat(efforts.sustained.getValue(180).meters).isCloseTo(900.0, 1e-9)
        assertThat(efforts.sustained.getValue(420).meters).isCloseTo(1_680.0, 1e-9)
        assertThat(efforts.sustained.getValue(720).meters).isCloseTo(2_880.0, 1e-9)
    }

    @Test
    fun `an interval session has best durations but no sustained 12 minutes`() {
        val reps = run(*Array(6) { listOf(Triple(240, 4.5, 175), Triple(120, 1.8, 150)) }.flatMap { it }.toTypedArray())

        val efforts = efforts(reps)

        assertThat(efforts.durations[720]).isNotNull()
        assertThat(efforts.sustained[720]).isNull()
        assertThat(efforts.sustained.getValue(180).meters).isCloseTo(810.0, 1e-9)
    }

    @Test
    fun `a fast stretch without a heart rate response is not an effort`() {
        // Running, two minutes of walking, then three minutes in a vehicle with heart rate at 110.
        val ride = run(Triple(300, 3.0, 150), Triple(120, 1.2, 120), Triple(180, 8.0, 110))

        val efforts = efforts(ride)

        assertThat(efforts.distances.getValue(RaceDistance.M400).seconds).isCloseTo(133.33, 0.5)
    }

    @Test
    fun `the best of many runs is kept per source`() {
        val slow = efforts(run(Triple(600, 3.0, 160)))
        val fast = efforts(run(Triple(600, 4.0, 170)), EffortSource.WATCH)

        val bests = BestEfforts.best(listOf(slow, fast))
        val k1 = bests.distances.getValue(RaceDistance.K1)

        assertThat(k1.gps!!.seconds).isCloseTo(333.33, 0.5)
        assertThat(k1.watch!!.seconds).isCloseTo(250.0, 0.5)
        assertThat(k1.best).isEqualTo(k1.watch)
        assertThat(bests.isEmpty()).isFalse()
    }
}
