package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isSameInstanceAs
import org.junit.jupiter.api.Test

class SpeedCleaningTest {
    @Test
    fun `implausible values are removed together with their timestamps`() {
        val speed = SampleSeries(longArrayOf(0, 1_000, 2_000, 3_000), doubleArrayOf(4.0, 87.7, Double.NaN, 4.2))

        val clean = SpeedCleaning.dropImplausible(speed, maxSpeed = 11.0)

        assertThat(clean.times.toList()).containsExactly(0L, 3_000L)
        assertThat(clean.values.toList()).containsExactly(4.0, 4.2)
    }

    @Test
    fun `clean input is returned unchanged`() {
        val speed = SampleSeries(longArrayOf(0, 1_000), doubleArrayOf(3.0, 3.1))

        assertThat(SpeedCleaning.dropImplausible(speed, 11.0)).isSameInstanceAs(speed)
    }

    @Test
    fun `integration holds each speed until the next sample and caps gaps`() {
        val speed = SampleSeries(longArrayOf(0, 1_000, 2_000, 62_000), doubleArrayOf(4.0, 4.0, 2.0, 2.0))

        // 4 m * 2 s + 2 m/s * 5 s (60 s gap capped at 5 s).
        assertThat(SpeedCleaning.integrateMeters(speed, maxGapMillis = 5_000)).isCloseTo(18.0, 1e-9)
    }
}
