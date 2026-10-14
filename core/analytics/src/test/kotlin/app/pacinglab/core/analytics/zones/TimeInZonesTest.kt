package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class TimeInZonesTest {
    private val zones = Zones.of(
        ZoneModel.LTHR_FIVE,
        HeartRateAnchors(maxHr = 190, thresholdHr = 170, restingHr = 50),
    )

    @Test
    fun `each sample holds its heart rate until the next sample`() {
        val series = HeartRateSeries.of(
            0L to 120,
            1_000L to 150,
            3_000L to 180,
            4_000L to 180,
        )

        val result = TimeInZones.compute(series, zones)

        assertThat(result.millis.toList()).containsExactly(1_000L, 2_000L, 0L, 0L, 1_000L)
        assertThat(result.totalMillis).isEqualTo(4_000L)
    }

    @Test
    fun `gaps longer than the cap only count up to the cap`() {
        val series = HeartRateSeries.of(0L to 150, 60_000L to 150, 61_000L to 150)

        val result = TimeInZones.compute(series, zones, maxGapMillis = 10_000L)

        assertThat(result.millis[1]).isEqualTo(11_000L)
    }

    @Test
    fun `empty and single-sample series have no time in any zone`() {
        assertThat(TimeInZones.compute(HeartRateSeries.EMPTY, zones).totalMillis).isEqualTo(0L)
        assertThat(TimeInZones.compute(HeartRateSeries.of(0L to 150), zones).totalMillis).isEqualTo(0L)
    }

    @Test
    fun `fractions are zero when there is no data`() {
        val result = TimeInZones.compute(HeartRateSeries.EMPTY, zones)

        assertThat(result.fraction(0)).isEqualTo(0.0)
    }

    @Test
    fun `total time equals the sum of capped sample intervals`() = runTest {
        checkAll(Arb.list(Arb.int(1..20_000), 0..300), Arb.long(1L..30_000L)) { steps, cap ->
            val series = seriesFromSteps(steps)

            val result = TimeInZones.compute(series, zones, maxGapMillis = cap)

            assertThat(result.totalMillis).isEqualTo(steps.sumOf { minOf(it.toLong(), cap) })
        }
    }

    @Test
    fun `shifting all timestamps does not change the distribution`() = runTest {
        checkAll(Arb.list(Arb.int(1..20_000), 0..200), Arb.long(0L..2_000_000_000_000L)) { steps, offset ->
            val series = seriesFromSteps(steps)
            val shifted = HeartRateSeries(LongArray(series.size) { series.times[it] + offset }, series.bpm)

            assertThat(TimeInZones.compute(shifted, zones).millis.toList())
                .isEqualTo(TimeInZones.compute(series, zones).millis.toList())
        }
    }

    private fun seriesFromSteps(steps: List<Int>): HeartRateSeries {
        val times = LongArray(steps.size + 1)
        for (i in steps.indices) times[i + 1] = times[i] + steps[i]
        // Deterministic heart rates sweeping through every zone.
        val bpm = IntArray(times.size) { 100 + (it * 7) % 100 }
        return HeartRateSeries(times, bpm)
    }
}
