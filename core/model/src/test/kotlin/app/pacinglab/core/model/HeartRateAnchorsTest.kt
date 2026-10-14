package app.pacinglab.core.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

class HeartRateAnchorsTest {
    @Test
    fun `reserve is max minus resting`() {
        assertThat(HeartRateAnchors(maxHr = 190, thresholdHr = 170, restingHr = 48).reserve).isEqualTo(142)
    }

    @Test
    fun `threshold must lie strictly between resting and max`() {
        assertFailure { HeartRateAnchors(maxHr = 190, thresholdHr = 190, restingHr = 50) }
            .isInstanceOf<IllegalArgumentException>()
        assertFailure { HeartRateAnchors(maxHr = 190, thresholdHr = 50, restingHr = 50) }
            .isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `implausible values are rejected`() {
        assertFailure { HeartRateAnchors(maxHr = 300, thresholdHr = 170, restingHr = 50) }
            .isInstanceOf<IllegalArgumentException>()
        assertFailure { HeartRateAnchors(maxHr = 190, thresholdHr = 170, restingHr = 10) }
            .isInstanceOf<IllegalArgumentException>()
    }
}
