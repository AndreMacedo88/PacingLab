package app.pacinglab.core.analytics.performance

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AerobicEfficiencyTest {
    private val threshold = 167

    private fun run(seconds: Int, speed: Double, bpm: Int) = Triple(
        HeartRateSeries(LongArray(seconds) { it * 1_000L }, IntArray(seconds) { bpm }),
        SampleSeries(LongArray(seconds) { it * 1_000L }, DoubleArray(seconds) { speed }),
        seconds * 1_000L,
    )

    @Test
    fun `steady easy running gives its speed scaled to the reference heart rate`() {
        // 3.2 m/s at 145 bpm (87 % of 167); at 85 % (141.95 bpm) that means 3.2 x 141.95 / 145.
        val (hr, speed, end) = run(30 * 60, 3.2, 145)

        val estimate = AerobicEfficiency.of(hr, speed, 0, end, threshold)!!

        assertThat(estimate.speedAtReference).isCloseTo(3.2 * 0.85 * 167 / 145, 1e-9)
        assertThat(AerobicEfficiency.referenceHr(threshold)).isEqualTo(142)
    }

    @Test
    fun `hard running, very easy running and short runs give nothing`() {
        val (hardHr, hardSpeed, hardEnd) = run(30 * 60, 4.0, 165)
        assertThat(AerobicEfficiency.of(hardHr, hardSpeed, 0, hardEnd, threshold)).isNull()
        val (easyHr, easySpeed, easyEnd) = run(30 * 60, 2.6, 120)
        assertThat(AerobicEfficiency.of(easyHr, easySpeed, 0, easyEnd, threshold)).isNull()
        val (shortHr, shortSpeed, shortEnd) = run(4 * 60, 3.2, 145)
        assertThat(AerobicEfficiency.of(shortHr, shortSpeed, 0, shortEnd, threshold)).isNull()
    }

    @Test
    fun `the trend averages the runs of the last six weeks, weighted by stretches`() {
        val day = LocalDate.of(2026, 10, 22)
        val points = listOf(
            EfficiencyPoint(day.minusDays(60), 2.5, 10),
            EfficiencyPoint(day.minusDays(10), 3.0, 10),
            EfficiencyPoint(day, 3.3, 30),
        )

        val trend = AerobicEfficiency.trend(points, day)

        assertThat(trend.last().speedAtReference).isCloseTo((3.0 * 10 + 3.3 * 30) / 40, 1e-9)
        assertThat(trend.first().date).isEqualTo(day.minusDays(60))
    }
}
