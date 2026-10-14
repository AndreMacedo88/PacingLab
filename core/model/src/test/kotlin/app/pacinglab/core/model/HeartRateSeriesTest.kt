package app.pacinglab.core.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test

class HeartRateSeriesTest {
    @Test
    fun `timestamps must be strictly increasing`() {
        assertFailure { HeartRateSeries.of(0L to 100, 0L to 101) }.isInstanceOf<IllegalArgumentException>()
        assertFailure { HeartRateSeries.of(5L to 100, 1L to 101) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `arrays must have equal length`() {
        assertFailure { HeartRateSeries(longArrayOf(0, 1), intArrayOf(100)) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `equality compares contents`() {
        assertThat(HeartRateSeries.of(0L to 100, 1L to 101)).isEqualTo(HeartRateSeries.of(0L to 100, 1L to 101))
        assertThat(HeartRateSeries.of(0L to 100)).isNotEqualTo(HeartRateSeries.of(0L to 101))
    }
}
