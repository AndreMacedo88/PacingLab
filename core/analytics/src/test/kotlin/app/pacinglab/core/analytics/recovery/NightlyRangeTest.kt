package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.RangeStanding
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class NightlyRangeTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun night(daysAgo: Long, hrv: Double? = null, resting: Double? = null) = Night(
        date = today.minusDays(daysAgo),
        start = Instant.EPOCH,
        end = Instant.EPOCH,
        asleepMillis = 0,
        stageMillis = emptyMap(),
        hrvMs = hrv,
        restingHr = resting,
        respiratoryRate = null,
    )

    @Test
    fun `the range spans the 10th to 90th percentile of the nights before`() {
        // Nightly HRV 41 to 80 ms over the last 40 nights.
        val nights = (1L..40L).map { night(it, hrv = 40.0 + it) }

        val range = NightlyRange.of(nights, NightlyMetric.HRV, today)!!

        assertThat(range.low).isCloseTo(44.9, 1e-9)
        assertThat(range.high).isCloseTo(76.1, 1e-9)
        assertThat(range.nights).isEqualTo(40)
        assertThat(range.standing(43.0)).isEqualTo(RangeStanding.BELOW)
        assertThat(range.standing(60.0)).isEqualTo(RangeStanding.IN_RANGE)
    }

    @Test
    fun `tonight and nights outside the window do not count, and two weeks are needed`() {
        val nights = (0L..13L).map { night(it, hrv = 60.0) } + night(70, hrv = 10.0)

        assertThat(NightlyRange.of(nights, NightlyMetric.HRV, today)).isNull()
        assertThat(NightlyRange.of(nights + night(14, hrv = 60.0), NightlyMetric.HRV, today)).isNotNull()
    }

    @Test
    fun `whole-beat values get a range of at least two beats either side`() {
        val nights = (1L..20L).map { night(it, resting = 57.0) }

        val range = NightlyRange.of(nights, NightlyMetric.RESTING_HR, today)!!

        assertThat(range.low).isCloseTo(55.0, 1e-9)
        assertThat(range.high).isCloseTo(59.0, 1e-9)
    }
}
