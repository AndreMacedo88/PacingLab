package app.pacinglab.core.analytics.performance

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class AerobicDecouplingTest {
    private fun run(seconds: Int, speed: (Int) -> Double, bpm: (Int) -> Int): Triple<HeartRateSeries, SampleSeries, Long> {
        val times = LongArray(seconds) { it * 1_000L }
        return Triple(HeartRateSeries(times, IntArray(seconds, bpm)), SampleSeries(times, DoubleArray(seconds, speed)), seconds * 1_000L)
    }

    @Test
    fun `heart rate drifting up at the same pace shows as decoupling`() {
        // After a 10-minute warm-up, 25 minutes at 140 bpm and 25 at 147, all at 3 m/s.
        val (hr, speed, end) = run(60 * 60, { 3.0 }, { if (it < 35 * 60) 140 else 147 })

        val decoupling = AerobicDecoupling.of(hr, speed, 0, end)!!

        assertThat(decoupling.percent).isCloseTo((1 - 140.0 / 147) * 100, 1e-9)
        assertThat(decoupling.minutes).isEqualTo(50)
    }

    @Test
    fun `intervals, short runs and runs with long stops are left out`() {
        val (ihr, ispeed, iend) = run(60 * 60, { if (it / 120 % 2 == 0) 4.5 else 2.0 }, { 160 })
        assertThat(AerobicDecoupling.of(ihr, ispeed, 0, iend)).isNull()

        val (shr, sspeed, send) = run(35 * 60, { 3.0 }, { 145 })
        assertThat(AerobicDecoupling.of(shr, sspeed, 0, send)).isNull()

        val (phr, pspeed, pend) = run(60 * 60, { if (it in 1_800 until 2_400) 0.0 else 3.0 }, { 145 })
        assertThat(AerobicDecoupling.of(phr, pspeed, 0, pend)).isNull()
    }
}
