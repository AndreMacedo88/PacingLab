package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.isCloseTo
import org.junit.jupiter.api.Test

class FitnessAgeTest {
    @Test
    fun `a decade's average VO2max has the age of the middle of the decade`() {
        assertThat(FitnessAge.of(49.0)).isCloseTo(35.0, 1e-9)
        assertThat(FitnessAge.of(42.0)).isCloseTo(55.0, 1e-9)
        assertThat(FitnessAge.of(38.0, female = true)).isCloseTo(45.0, 1e-9)
    }

    @Test
    fun `values between decades are interpolated`() {
        assertThat(FitnessAge.of(51.5)).isCloseTo(30.0, 1e-9)
        assertThat(FitnessAge.average(30.0)).isCloseTo(51.5, 1e-9)
    }

    @Test
    fun `ages beyond the reference are extended and kept within 20 to 90`() {
        assertThat(FitnessAge.of(55.0)).isCloseTo(23.0, 1e-9)
        assertThat(FitnessAge.of(70.0)).isCloseTo(20.0, 1e-9)
        assertThat(FitnessAge.of(15.0)).isCloseTo(90.0, 1e-9)
    }
}
