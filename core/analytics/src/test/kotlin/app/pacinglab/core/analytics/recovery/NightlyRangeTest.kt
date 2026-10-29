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

        // 44.9 and 76.1, at the whole milliseconds HRV is shown in.
        assertThat(range.low).isEqualTo(45.0)
        assertThat(range.high).isEqualTo(76.0)
        assertThat(range.nights).isEqualTo(40)
        assertThat(range.standing(43.0)).isEqualTo(RangeStanding.BELOW)
        assertThat(range.standing(60.0)).isEqualTo(RangeStanding.IN_RANGE)
    }

    @Test
    fun `tonight and nights outside the window do not count, and two weeks are needed`() {
        val nights = (0L..13L).map { night(it, hrv = 60.0) } + night(70, hrv = 10.0)

        assertThat(NightlyRange.of(nights, NightlyMetric.HRV, today)).isNull()
        assertThat(NightlyRange.nightsBefore(nights, NightlyMetric.HRV, today)).isEqualTo(13)
        assertThat(NightlyRange.of(nights + night(14, hrv = 60.0), NightlyMetric.HRV, today)).isNotNull()
    }

    @Test
    fun `whole-beat values get a range of at least two beats either side`() {
        val nights = (1L..20L).map { night(it, resting = 57.0) }

        val range = NightlyRange.of(nights, NightlyMetric.RESTING_HR, today)!!

        assertThat(range.low).isCloseTo(55.0, 1e-9)
        assertThat(range.high).isCloseTo(59.0, 1e-9)
    }

    @Test
    fun `a value shown equal to the end of its range is inside it`() {
        val range = NightlyRange(94.5, 95.8, nights = 30, metric = NightlyMetric.OXYGEN_SATURATION)

        assertThat(range.standing(95.84)).isEqualTo(RangeStanding.IN_RANGE)
        assertThat(range.standing(94.46)).isEqualTo(RangeStanding.IN_RANGE)
        assertThat(range.standing(95.86)).isEqualTo(RangeStanding.ABOVE)
        assertThat(range.standing(94.44)).isEqualTo(RangeStanding.BELOW)
    }
}
