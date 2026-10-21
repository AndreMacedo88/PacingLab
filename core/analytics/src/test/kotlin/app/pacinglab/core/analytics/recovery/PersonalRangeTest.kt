package app.pacinglab.core.analytics.recovery

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersonalRangeTest {
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `the range is the mean of the month before plus or minus one standard deviation`() {
        // 20 and 24 alternating: mean 22, sample SD about 2.05.
        val values = (1..10).associate { today.minusDays(it.toLong()) to if (it % 2 == 0) 20.0 else 24.0 }

        val range = PersonalRange.of(values, today)!!

        assertThat(range.start).isCloseTo(22 - 2.108, 0.01)
        assertThat(range.endInclusive).isCloseTo(22 + 2.108, 0.01)
    }

    @Test
    fun `the day itself and days over a month back are left out, and a week is needed`() {
        val values = mapOf(today to 99.0) + (31..40).associate { today.minusDays(it.toLong()) to 50.0 } +
            (1..6).associate { today.minusDays(it.toLong()) to 20.0 }

        assertThat(PersonalRange.of(values, today)).isNull()
    }

    @Test
    fun `a steady measure still gets a range of the minimum width`() {
        val values = (1..10).associate { today.minusDays(it.toLong()) to 18.0 }

        val range = PersonalRange.of(values, today, minHalfWidth = 1.5)!!

        assertThat(range.start).isCloseTo(16.5, 1e-9)
        assertThat(range.endInclusive).isCloseTo(19.5, 1e-9)
    }
}
