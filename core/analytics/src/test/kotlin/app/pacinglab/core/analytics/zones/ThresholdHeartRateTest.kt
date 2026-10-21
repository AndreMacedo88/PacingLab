package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ThresholdHeartRateTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun run(seconds: Int, speed: (Int) -> Double, bpm: (Int) -> Int) = Triple(
        HeartRateSeries(LongArray(seconds) { it * 1_000L }, IntArray(seconds) { bpm(it) }),
        SampleSeries(LongArray(seconds) { it * 1_000L }, DoubleArray(seconds) { speed(it) }),
        seconds * 1_000L,
    )

    @Test
    fun `a steady half hour gives the average of its last 20 minutes`() {
        // Heart rate climbs from 140 to 170 over the first ten minutes, then holds.
        val (hr, speed, end) = run(30 * 60, speed = { 3.6 }, bpm = { t -> if (t < 600) 140 + t / 20 else 170 })

        assertThat(ThresholdHeartRateEstimate.effort(hr, speed, 0, end)!!).isCloseTo(170.0, 1e-9)
    }

    @Test
    fun `the hardest half hour of a longer run counts`() {
        // 20 easy minutes at 150, then 40 harder minutes at 165.
        val (hr, speed, end) = run(60 * 60, speed = { t -> if (t < 1200) 3.0 else 3.8 }, bpm = { t -> if (t < 1200) 150 else 165 })

        assertThat(ThresholdHeartRateEstimate.effort(hr, speed, 0, end)!!).isCloseTo(165.0, 1e-9)
    }

    @Test
    fun `walking breaks and short runs give nothing`() {
        // A minute of walking every ten minutes: never 30 minutes of running in a row.
        val (hr, speed, end) = run(60 * 60, speed = { t -> if (t % 600 < 60) 1.4 else 3.6 }, bpm = { 168 })
        assertThat(ThresholdHeartRateEstimate.effort(hr, speed, 0, end)).isNull()

        val (shortHr, shortSpeed, shortEnd) = run(25 * 60, speed = { 3.6 }, bpm = { 168 })
        assertThat(ThresholdHeartRateEstimate.effort(shortHr, shortSpeed, 0, shortEnd)).isNull()
    }

    @Test
    fun `the hardest recent effort sets the value and easy ones are ignored`() {
        val efforts = listOf(
            ThresholdEffort("easy", today.minusDays(5), 150.0),
            ThresholdEffort("hard", today.minusDays(60), 166.7),
            ThresholdEffort("older", today.minusDays(400), 172.0),
        )

        assertThat(ThresholdHeartRateEstimate.of(efforts, maxHr = 186, date = today))
            .isEqualTo(ThresholdHeartRateEstimate(167, today.minusDays(60)))
        // Before the hard run, the older one is the latest within a year.
        assertThat(ThresholdHeartRateEstimate.of(efforts, maxHr = 186, date = today.minusDays(61)))
            .isEqualTo(ThresholdHeartRateEstimate(172, today.minusDays(400)))
        // 150 is below 85 % of 186: an easy run alone says nothing about the threshold.
        assertThat(ThresholdHeartRateEstimate.of(efforts.take(1), maxHr = 186, date = today)).isNull()
    }
}
