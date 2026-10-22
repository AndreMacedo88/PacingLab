package app.pacinglab.core.analytics.load

import assertk.assertThat
import assertk.assertions.isCloseTo
import org.junit.jupiter.api.Test

class EffortLoadTest {
    @Test
    fun `session RPE is rating times minutes and the stress score scales the hour by the rating`() {
        val seventySixMinutes = 76 * 60_000L

        assertThat(EffortLoad.sessionRpe(6, seventySixMinutes)).isCloseTo(456.0, 1e-9)
        assertThat(EffortLoad.stressScore(6, seventySixMinutes)).isCloseTo(70.0 * 76 / 60, 1e-9)
        assertThat(EffortLoad.stressScore(9, 3_600_000L)).isCloseTo(100.0, 1e-9)
    }
}
