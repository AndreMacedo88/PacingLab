package app.pacinglab.core.healthconnect.probe

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Test

class SpacingStatsTest {
    @Test
    fun `one hertz stream has one second median and full 1 s share`() {
        val stats = SpacingStats.of(LongArray(61) { it * 1_000L })

        assertThat(stats.samples).isEqualTo(61)
        assertThat(stats.medianGapMs).isEqualTo(1_000L)
        assertThat(stats.shareWithin1s).isNotNull().isCloseTo(1.0, 1e-9)
    }

    @Test
    fun `unsorted input and duplicates are handled`() {
        val stats = SpacingStats.of(longArrayOf(10_000, 0, 5_000, 5_000, 20_000))

        assertThat(stats.duplicates).isEqualTo(1)
        assertThat(stats.medianGapMs).isEqualTo(5_000L)
        assertThat(stats.maxGapMs).isEqualTo(10_000L)
        assertThat(stats.shareWithin5s).isNotNull().isCloseTo(2.0 / 3.0, 1e-9)
    }

    @Test
    fun `fewer than two timestamps give no gap statistics`() {
        val stats = SpacingStats.of(longArrayOf(42))

        assertThat(stats.medianGapMs).isNull()
        assertThat(stats.shareWithin1s).isNull()
    }

    @Test
    fun `percentiles use nearest rank`() {
        // Gaps: 1, 2, ..., 10 seconds.
        val times = LongArray(11)
        for (i in 1..10) times[i] = times[i - 1] + i * 1_000L

        val stats = SpacingStats.of(times)

        assertThat(stats.p10GapMs).isEqualTo(1_000L)
        assertThat(stats.medianGapMs).isEqualTo(5_000L)
        assertThat(stats.p90GapMs).isEqualTo(9_000L)
    }

    @Test
    fun `haversine matches a known distance`() {
        // Copenhagen City Hall to Aarhus City Hall, about 156.8 km apart.
        val meters = haversineMeters(55.6761, 12.5683, 56.1527, 10.2039)

        assertThat(meters).isCloseTo(156_800.0, 1_500.0)
    }
}
