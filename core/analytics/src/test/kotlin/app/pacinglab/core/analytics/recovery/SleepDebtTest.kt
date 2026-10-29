package app.pacinglab.core.analytics.recovery

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class SleepDebtTest {
    private val day = LocalDate.of(2026, 11, 20)
    private val hour = 3_600_000L

    private fun night(date: LocalDate, asleep: Long): Night {
        val end = Instant.parse("${date}T06:00:00Z")
        return Night(date, end.minusMillis(asleep), end, asleep, emptyMap(), null, null, null)
    }

    @Test
    fun `an hour short every night weighs recent nights more`() {
        val nights = (13 downTo 0).map { night(day.minusDays(it.toLong()), 7 * hour) }

        val debt = SleepDebt.of(nights, nights.associate { it.date to 8 * hour }, day)!!

        // Weights 14/14 down to 1/14 add up to 7.5.
        assertThat(debt.millis.toDouble()).isCloseTo(7.5 * hour, 2.0)
        assertThat(debt.level).isEqualTo(SleepDebtLevel.HIGH)
    }

    @Test
    fun `a long night pays back half its surplus`() {
        val nights = listOf(
            night(day.minusDays(4), 8 * hour),
            night(day.minusDays(3), 8 * hour),
            night(day.minusDays(2), 8 * hour),
            night(day.minusDays(1), 6 * hour),
            night(day, 10 * hour),
        )

        val debt = SleepDebt.of(nights, nights.associate { it.date to 8 * hour }, day)!!

        // Two hours short yesterday (weight 13/14) less half of two hours over last night (weight 1).
        assertThat(debt.millis.toDouble()).isCloseTo(2 * hour * 13.0 / 14 - hour, 2.0)
        assertThat(debt.level).isEqualTo(SleepDebtLevel.LOW)
    }

    @Test
    fun `more sleep than needed leaves no debt`() {
        val nights = (6 downTo 0).map { night(day.minusDays(it.toLong()), 9 * hour) }

        val debt = SleepDebt.of(nights, nights.associate { it.date to 8 * hour }, day)!!

        assertThat(debt.millis).isEqualTo(0L)
        assertThat(debt.level).isEqualTo(SleepDebtLevel.NONE)
    }

    @Test
    fun `fewer than five nights in two weeks give no debt`() {
        val nights = (3 downTo 0).map { night(day.minusDays(it.toLong()), 6 * hour) } + night(day.minusDays(20), 6 * hour)

        assertThat(SleepDebt.of(nights, nights.associate { it.date to 8 * hour }, day)).isNull()
    }
}
