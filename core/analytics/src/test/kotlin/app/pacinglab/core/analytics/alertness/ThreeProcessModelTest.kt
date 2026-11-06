package app.pacinglab.core.analytics.alertness

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp

class ThreeProcessModelTest {
    private val model = ThreeProcessModel()
    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L

    @Test
    fun `sleep pressure falls towards its lower asymptote while awake`() {
        assertThat(model.awake(14.0, 16.0)).isCloseTo((14.0 - 2.4) * exp(-0.0353 * 16) + 2.4, 1e-12)
        assertThat(model.awake(14.0, 1000.0)).isCloseTo(2.4, 1e-6)
    }

    @Test
    fun `recovery below the break is linear at the slope it has there`() {
        // From the paper's 7.96 at retiring: 6.62 h of linear recovery at 0.381 x 1.8 an hour, then exponential.
        val slope = 0.381 * (14.3 - 12.5)
        val toBreak = (12.5 - 7.96) / slope
        assertThat(model.asleep(7.96, 2.0)).isCloseTo(7.96 + 2 * slope, 1e-12)
        assertThat(model.asleep(7.96, 8.0)).isCloseTo(14.3 - 1.8 * exp(-0.381 * (8 - toBreak)), 1e-12)
        assertThat(model.asleep(13.0, 2.0)).isCloseTo(14.3 - 1.3 * exp(-0.762), 1e-12)
    }

    @Test
    fun `a longer sleep need slows recovery so that it takes the whole need`() {
        val longer = ThreeProcessModel(needHours = 9.0)
        assertThat(longer.asleep(13.0, 2.0)).isCloseTo(14.3 - 1.3 * exp(-0.381 * 8 / 9 * 2), 1e-12)
        assertThat(longer.asleep(13.0, 2.0)).isLessThan(model.asleep(13.0, 2.0))
    }

    @Test
    fun `the circadian, ultradian and inertia parts have the published shape`() {
        assertThat(model.circadian(16.8)).isCloseTo(2.5, 1e-12)
        assertThat(model.circadian(4.8)).isCloseTo(-2.5, 1e-12)
        assertThat(model.ultradian(13.8)).isCloseTo(-0.5, 1e-12)
        assertThat(model.ultradian(19.8)).isCloseTo(0.5, 1e-12)
        assertThat(model.inertia(0.0)).isCloseTo(-5.7, 1e-12)
        assertThat(ThreeProcessModel(phaseShiftHours = 1.5).circadian(18.3)).isCloseTo(2.5, 1e-12)
    }

    @Test
    fun `a run integrates exactly, whatever the grid`() {
        val start = LocalDate.of(2026, 1, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        // Nights from 23:10 to 07:05, off the grid.
        val nights = (0 until 4).map { start + it * 24 * hour - 50 * 60_000L until start + it * 24 * hour + 7 * hour + 5 * 60_000L }
        val fine = model.run(nights, start - 2 * hour, start + 4 * 24 * hour, 60_000L, zone, 12.0)
        val coarse = model.run(nights, start - 2 * hour, start + 4 * 24 * hour, 5 * 60_000L, zone, 12.0)
        for (i in 0 until coarse.size) {
            val j = i * 5
            assertThat(coarse.times[i]).isEqualTo(fine.times[j])
            assertThat(coarse.pressure[i]).isCloseTo(fine.pressure[j], 1e-9)
            if (!coarse.values[i].isNaN()) assertThat(coarse.values[i]).isCloseTo(fine.values[j], 1e-9)
        }
    }

    @Test
    fun `a regular day starts groggy, peaks in the afternoon and has no alertness during sleep`() {
        val start = LocalDate.of(2026, 1, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val nights = (0 until 6).map { start + it * 24 * hour - hour until start + it * 24 * hour + 7 * hour }
        val series = model.run(nights, start - 2 * hour, start + 6 * 24 * hour, 5 * 60_000L, zone, 12.0)
        val day = start + 5 * 24 * hour
        assertThat(series.at(day + 3 * hour)).isNull()
        val waking = checkNotNull(series.at(day + 7 * hour))
        val afternoon = (13..19).map { checkNotNull(series.at(day + it * hour)) }
        assertThat(waking).isBetween(5.0, 8.5)
        assertThat(afternoon.max()).isGreaterThan(12.5)
        assertThat((15..18).any { checkNotNull(series.at(day + it * hour)) == afternoon.max() }).isTrue()
        assertThat(checkNotNull(series.at(day + 22 * hour))).isLessThan(afternoon.max() - 1.5)
    }
}
