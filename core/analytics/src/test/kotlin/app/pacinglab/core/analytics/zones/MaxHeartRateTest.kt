package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MaxHeartRateTest {
    private val today = LocalDate.of(2026, 10, 21)

    /** One sample per second from the given values. */
    private fun series(vararg bpm: Int) = HeartRateSeries(LongArray(bpm.size) { it * 1_000L }, bpm)

    private fun peak(id: String, bpm: Double, daysAgo: Long = 10) = HeartRatePeak(id, today.minusDays(daysAgo), bpm)

    @Test
    fun `the sustained peak averages ten seconds and ignores a lone spike`() {
        val hr = IntArray(60) { 170 }.also {
            it[20] = 199 // optical spike
            for (i in 40 until 52) it[i] = 182
        }

        assertThat(MaxHeartRateEstimate.sustainedPeak(series(*hr))!!).isCloseTo(182.0, 1e-9)
    }

    @Test
    fun `gaps longer than five seconds break the window`() {
        // Samples every 7 s leave a second without data after each five-second hold.
        val sparse = HeartRateSeries(LongArray(20) { it * 7_000L }, IntArray(20) { 180 })

        assertThat(MaxHeartRateEstimate.sustainedPeak(sparse)).isNull()
        // Samples every 2 s are held and do.
        val dense = HeartRateSeries(LongArray(20) { it * 2_000L }, IntArray(20) { 180 })
        assertThat(MaxHeartRateEstimate.sustainedPeak(dense)!!).isCloseTo(180.0, 1e-9)
    }

    @Test
    fun `the highest peak counts once another workout came within 3 bpm`() {
        val estimate = MaxHeartRateEstimate.of(listOf(peak("a", 186.1, 29), peak("b", 184.0, 124), peak("c", 181.0)), today)!!

        assertThat(estimate.bpm).isEqualTo(186)
        assertThat(estimate.reachedOn).isEqualTo(today.minusDays(29))
    }

    @Test
    fun `an unconfirmed outlier gives way to the next confirmed peak`() {
        val estimate = MaxHeartRateEstimate.of(listOf(peak("glitch", 197.0), peak("b", 184.0), peak("c", 182.5)), today)!!

        assertThat(estimate.bpm).isEqualTo(184)
    }

    @Test
    fun `old workouts, easy sessions and a single workout give no estimate`() {
        assertThat(MaxHeartRateEstimate.of(listOf(peak("a", 186.0, daysAgo = 900), peak("b", 185.0, daysAgo = 800)), today)).isNull()
        assertThat(MaxHeartRateEstimate.of(listOf(peak("a", 110.0), peak("b", 109.0)), today)).isNull()
        assertThat(MaxHeartRateEstimate.of(listOf(peak("a", 186.0)), today)).isNull()
    }
}
