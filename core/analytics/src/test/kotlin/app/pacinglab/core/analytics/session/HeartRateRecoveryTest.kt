package app.pacinglab.core.analytics.session

import app.pacinglab.core.model.HeartRateSeries
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class HeartRateRecoveryTest {
    /** One sample a second: rising to 178 by the end of the rep at 240 s, peaking 4 s later, then falling 0.5 bpm a second. */
    private val series = HeartRateSeries(
        LongArray(600) { it * 1_000L },
        IntArray(600) { s ->
            when {
                s <= 240 -> 150 + 28 * s / 240
                s <= 244 -> 178 + (s - 240) / 2
                else -> 180 - (s - 244) / 2
            }
        },
    )

    @Test
    fun `the drop runs from the lagging peak to the heart rate one minute after the rep`() {
        // Peak 180 at 244 s; at 300 s heart rate is 180 - 28 = 152, the five seconds up to it average 153.2.
        assertThat(HeartRateRecovery.of(series, repEnd = 240_000, recoveryEnd = 360_000)).isEqualTo(HeartRateDrop(27, 60))
    }

    @Test
    fun `a short recovery is measured to its end, a very short one not at all`() {
        assertThat(HeartRateRecovery.of(series, repEnd = 240_000, recoveryEnd = 285_000)!!.seconds).isEqualTo(45)
        assertThat(HeartRateRecovery.of(series, repEnd = 240_000, recoveryEnd = 260_000)).isNull()
    }
}
