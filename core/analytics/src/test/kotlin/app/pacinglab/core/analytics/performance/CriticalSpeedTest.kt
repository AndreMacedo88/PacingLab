package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CriticalSpeedTest {
    private val today = LocalDate.of(2026, 10, 22)

    private fun effort(seconds: Int, meters: Double, daysAgo: Long = 10, source: EffortSource = EffortSource.GPS) =
        Effort("w$seconds", today.minusDays(daysAgo), source, 0, seconds.toDouble(), meters)

    private fun runs(vararg efforts: Effort) = efforts.map { RunEfforts(emptyMap(), emptyMap(), mapOf(it.seconds.toInt() to it)) }

    @Test
    fun `efforts on the model give back critical speed and D prime`() {
        // CS 4 m/s and D' 200 m: 920 m in 3 min, 1,880 m in 7 min, 3,080 m in 12 min.
        val result = CriticalSpeed.of(runs(effort(180, 920.0), effort(420, 1_880.0), effort(720, 3_080.0)), today)

        val fit = result.fit!!
        assertThat(fit.speed).isCloseTo(4.0, 1e-9)
        assertThat(fit.dPrime).isCloseTo(200.0, 1e-9)
        assertThat(fit.secondsFor(5_000.0)!!).isCloseTo(1_200.0, 1e-9)
        assertThat(fit.secondsFor(10_000.0)).isNull()
    }

    @Test
    fun `without a sustained 12 minutes there is no fit, only the reason`() {
        val result = CriticalSpeed.of(runs(effort(180, 920.0), effort(720, 3_080.0, daysAgo = 120)), today)

        assertThat(result.fit).isNull()
        assertThat(result.gap).isEqualTo(CriticalSpeedGap.NO_LONG_EFFORT)
        assertThat(result.efforts.size).isEqualTo(1)
    }

    @Test
    fun `efforts that are not all out are rejected`() {
        // A fast 3 minutes and a long run far below it put D' below zero.
        val result = CriticalSpeed.of(runs(effort(180, 600.0), effort(720, 3_500.0)), today)

        assertThat(result.gap).isEqualTo(CriticalSpeedGap.INCONSISTENT)
    }

    @Test
    fun `a fit using watch speed is marked as an estimate`() {
        val fit = CriticalSpeed.fit(listOf(effort(180, 920.0, source = EffortSource.WATCH), effort(720, 3_080.0)))

        assertThat(fit).isNotNull()
        assertThat(fit!!.estimated).isTrue()
    }
}
