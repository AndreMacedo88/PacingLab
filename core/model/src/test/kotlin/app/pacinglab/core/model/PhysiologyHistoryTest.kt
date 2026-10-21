package app.pacinglab.core.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PhysiologyHistoryTest {
    private val spring = PhysiologyEntry(LocalDate.of(2026, 4, 1), maxHr = 184)
    private val autumn = PhysiologyEntry(LocalDate.of(2026, 10, 1), maxHr = 186, thresholdHr = 170)
    private val history = PhysiologyHistory(listOf(autumn, spring))

    @Test
    fun `an entry applies from its date until the next one`() {
        assertThat(history.at(LocalDate.of(2026, 4, 1))).isEqualTo(spring)
        assertThat(history.at(LocalDate.of(2026, 9, 30))).isEqualTo(spring)
        assertThat(history.at(LocalDate.of(2026, 10, 1))).isEqualTo(autumn)
        assertThat(history.at(LocalDate.of(2027, 1, 1))).isEqualTo(autumn)
    }

    @Test
    fun `dates before the first entry use the first entry`() {
        assertThat(history.at(LocalDate.of(2025, 8, 14))).isEqualTo(spring)
    }

    @Test
    fun `entries are kept in date order and the latest is current`() {
        assertThat(history.entries).isEqualTo(listOf(spring, autumn))
        assertThat(history.current).isEqualTo(autumn)
    }

    @Test
    fun `an empty history has no values`() {
        assertThat(PhysiologyHistory.EMPTY.at(LocalDate.of(2026, 1, 1))).isNull()
        assertThat(PhysiologyHistory.EMPTY.current).isNull()
    }

    @Test
    fun `two entries on one day are rejected`() {
        assertFailure { PhysiologyHistory(listOf(spring, spring.copy(maxHr = 190))) }
    }

    @Test
    fun `implausible values are rejected`() {
        assertFailure { PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 100) }
        assertFailure { PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 186, thresholdHr = 186) }
        assertFailure { PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 186, thresholdHr = 60) }
        assertFailure { PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 186, restingHr = 15) }
        assertFailure { PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 186, thresholdHr = 100, restingHr = 110) }
    }
}
