package app.pacinglab.core.analytics.performance

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import kotlin.math.sin

class RunVo2MaxTest {
    private val anchors = HeartRateAnchors(maxHr = 190, thresholdHr = 170, restingHr = 50)

    private fun run(seconds: Int, speed: (Int) -> Double, bpm: (Int) -> Int) = Triple(
        HeartRateSeries(LongArray(seconds) { it * 1_000L }, IntArray(seconds) { bpm(it) }),
        SampleSeries(LongArray(seconds) { it * 1_000L }, DoubleArray(seconds) { speed(it) }),
        seconds * 1_000L,
    )

    @Test
    fun `steady running gives the Swain estimate`() {
        // 4.0 m/s = 240 m/min costs 3.5 + 48 = 51.5 ml/kg/min; 160 bpm is 110/140 = 78.6 % of reserve.
        val (hr, speed, end) = run(20 * 60, speed = { 4.0 }, bpm = { 160 })

        val estimate = RunVo2Max.of(hr, speed, 0, end, anchors)!!

        assertThat(estimate.value).isCloseTo(3.5 + 48.0 / (110.0 / 140), 1e-6)
        assertThat(estimate.windows).isGreaterThan(50)
    }

    @Test
    fun `per-second speed noise averages out`() {
        val (hr, speed, end) = run(20 * 60, speed = { 4.0 * (1 + 0.08 * sin(it * 2.3)) }, bpm = { 160 })

        assertThat(RunVo2Max.of(hr, speed, 0, end, anchors)!!.value).isCloseTo(64.6, 0.5)
    }

    @Test
    fun `heart rate that is still climbing gives nothing`() {
        // 0.1 bpm a second: half-minute averages 3 bpm apart, more than the 1.5 bpm a plateau allows.
        val (hr, speed, end) = run(15 * 60, speed = { 4.0 }, bpm = { 100 + it / 10 })

        assertThat(RunVo2Max.of(hr, speed, 0, end, anchors)).isNull()
    }

    @Test
    fun `reps count from their plateau while walking recoveries do not`() {
        // Five 5-minute reps at 4.2 m/s with heart rate rising for 90 s to 168, 1-minute walks between.
        val cycle = 360
        val (hr, speed, end) = run(
            seconds = 120 + 5 * cycle,
            speed = { t ->
                if (t < 120) {
                    3.0
                } else if ((t - 120) % cycle < 300) {
                    4.2
                } else {
                    1.4
                }
            },
            bpm = { t ->
                val inCycle = (t - 120) % cycle
                when {
                    t < 120 -> 140
                    inCycle < 90 -> 140 + inCycle * 28 / 90
                    inCycle < 300 -> 168
                    else -> 140
                }
            },
        )

        val estimate = RunVo2Max.of(hr, speed, 0, end, anchors)!!

        // 4.2 m/s = 252 m/min costs 53.9; 168 bpm is 118/140 of reserve. Five stretches fit in each rep.
        assertThat(estimate.value).isCloseTo(3.5 + 50.4 / (118.0 / 140), 1e-6)
        assertThat(estimate.windows).isEqualTo(25)
    }

    @Test
    fun `a six-minute warm-up gives no estimate`() {
        val (hr, speed, end) = run(6 * 60, speed = { 3.2 }, bpm = { t -> minOf(150, 100 + t / 3) })

        assertThat(RunVo2Max.of(hr, speed, 0, end, anchors)).isNull()
    }

    @Test
    fun `walking, short runs and a missing resting heart rate give nothing`() {
        val (hr, speed, end) = run(20 * 60, speed = { 1.5 }, bpm = { 120 })
        assertThat(RunVo2Max.of(hr, speed, 0, end, anchors)).isNull()

        val (shortHr, shortSpeed, shortEnd) = run(200, speed = { 4.0 }, bpm = { 160 })
        assertThat(RunVo2Max.of(shortHr, shortSpeed, 0, shortEnd, anchors)).isNull()

        val (h, s, e) = run(20 * 60, speed = { 4.0 }, bpm = { 160 })
        assertThat(RunVo2Max.of(h, s, 0, e, anchors.copy(restingHr = null))).isNull()
    }
}
