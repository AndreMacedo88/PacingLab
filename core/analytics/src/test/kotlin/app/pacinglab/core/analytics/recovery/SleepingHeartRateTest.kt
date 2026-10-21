package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.HeartRateSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant

class SleepingHeartRateTest {
    private val start = Instant.parse("2026-10-20T21:30:00Z").toEpochMilli()

    /** One sample every [step] seconds for [minutes], heart rate from [bpm]. */
    private fun series(minutes: Int, step: Int = 10, bpm: (minute: Int) -> Int): HeartRateSeries {
        val n = minutes * 60 / step
        return HeartRateSeries(LongArray(n) { start + it * step * 1_000L }, IntArray(n) { bpm(it * step / 60) })
    }

    @Test
    fun `five-minute averages give the curve, its mean and its lowest stretch`() {
        // 56 bpm for the first hour, 48 for the second, 52 for the third.
        val hr = series(180) { minute ->
            if (minute < 60) {
                56
            } else if (minute < 120) {
                48
            } else {
                52
            }
        }

        val sleeping = SleepingHeartRate.of(hr)!!

        assertThat(sleeping.curve.size).isEqualTo(36)
        assertThat(sleeping.curve.first().first).isEqualTo(Instant.ofEpochMilli(start + 150_000))
        assertThat(sleeping.lowest).isCloseTo(48.0, 1e-9)
        assertThat(sleeping.average).isCloseTo(52.0, 1e-9)
    }

    @Test
    fun `a single restless five minutes is smoothed away`() {
        val hr = series(120) { minute -> if (minute in 60 until 65) 70 else 50 }

        val sleeping = SleepingHeartRate.of(hr)!!

        assertThat(sleeping.curve.maxOf { it.second }).isCloseTo(50.0, 1e-9)
        assertThat(sleeping.lowest).isCloseTo(50.0, 1e-9)
    }

    @Test
    fun `sparse five minutes are left out and a short record gives nothing`() {
        // A sample every two and a half minutes puts only two in each five minutes.
        val sparse = series(60, step = 150) { 50 }

        assertThat(SleepingHeartRate.of(sparse)).isNull()
        assertThat(SleepingHeartRate.of(series(20) { 50 })).isNull()
        assertThat(SleepingHeartRate.of(HeartRateSeries.EMPTY)).isNull()
    }
}
