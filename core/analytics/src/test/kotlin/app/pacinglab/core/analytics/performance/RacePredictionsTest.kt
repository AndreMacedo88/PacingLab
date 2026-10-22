package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class RacePredictionsTest {
    @Test
    fun `VO2max 50 gives Daniels' published times`() {
        // VDOT 50 in Daniels' Running Formula: 19:57, 41:21, 1:31:35 and 3:10:49.
        assertThat(RacePredictions.secondsFromVo2Max(50.0, 5_000.0)!!).isCloseTo(19 * 60 + 57.0, 5.0)
        assertThat(RacePredictions.secondsFromVo2Max(50.0, 10_000.0)!!).isCloseTo(41 * 60 + 21.0, 5.0)
        assertThat(RacePredictions.secondsFromVo2Max(50.0, 21_097.5)!!).isCloseTo(5_495.0, 10.0)
        assertThat(RacePredictions.secondsFromVo2Max(50.0, 42_195.0)!!).isCloseTo(11_449.0, 15.0)
    }

    @Test
    fun `critical speed predicts only races of up to half an hour`() {
        val fit = CriticalSpeedFit(4.0, 200.0, emptyList())

        val predictions = RacePredictions.of(vo2max = null, criticalSpeed = fit)

        assertThat(predictions[0].fromCriticalSpeed!!).isCloseTo(1_200.0, 1e-9)
        assertThat(predictions[0].fromVo2Max).isNull()
        assertThat(predictions[1].fromCriticalSpeed).isNull()
    }
}
