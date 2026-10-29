package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.RangeStanding
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.sqrt

class VitalsAndVariationTest {
    private val day = LocalDate.of(2026, 11, 20)

    private fun night(date: LocalDate, hrv: Double? = 60.0, resting: Double? = 50.0, breathing: Double? = 14.0): Night {
        val end = Instant.parse("${date}T06:00:00Z")
        return Night(date, end.minusSeconds(8 * 3600), end, 8 * 3_600_000L, emptyMap(), hrv, resting, breathing)
    }

    /** Thirty nights alternating a little around the usual values, then [last] as last night. */
    private fun history(last: Night): List<Night> = (30 downTo 1).map { ago ->
        val wobble = if (ago % 2 == 0) 1.0 else -1.0
        night(day.minusDays(ago.toLong()), hrv = 60 + 3 * wobble, resting = 50 + wobble, breathing = 14 + 0.2 * wobble)
    } + last

    @Test
    fun `one measure outside its range is no alert`() {
        val vitals = Vitals.of(history(night(day, hrv = 40.0)), day)!!

        assertThat(vitals.outside.map { it.metric }).containsExactly(NightlyMetric.HRV)
        assertThat(vitals.alert).isEqualTo(false)
    }

    @Test
    fun `two measures outside their ranges raise the alert`() {
        val vitals = Vitals.of(history(night(day, hrv = 40.0, resting = 58.0)), day)!!

        assertThat(vitals.alert).isEqualTo(true)
        assertThat(vitals.signs.first { it.metric == NightlyMetric.RESTING_HR }.standing).isEqualTo(RangeStanding.ABOVE)
    }

    @Test
    fun `measures without a value or range are not judged`() {
        val vitals = Vitals.of(history(night(day)), day)!!

        // SpO2 and skin temperature come only from the Google Health API.
        assertThat(vitals.judged).isEqualTo(3)
        assertThat(Vitals.of(history(night(day)), day.plusDays(1))).isNull()
    }

    @Test
    fun `HRV variation is the coefficient of variation of ln RMSSD over a week`() {
        val values = listOf(50.0, 60.0, 55.0, 65.0, 58.0, 62.0, 52.0)
        val nights = values.mapIndexed { i, v -> night(day.minusDays(6L - i), hrv = v) }

        val variation = HrvVariation.of(nights, day)!!

        val logs = values.map(::ln)
        val mean = logs.average()
        val sd = sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1))
        assertThat(variation.percent).isCloseTo(100 * sd / mean, 1e-9)
        assertThat(variation.range).isNull()
    }

    @Test
    fun `HRV variation needs five nights in the week and learns its usual range`() {
        val sparse = (0L..3L).map { night(day.minusDays(it), hrv = 60.0 + it) }
        assertThat(HrvVariation.of(sparse, day)).isNull()

        val long = (0L..80L).map { night(day.minusDays(it), hrv = if (it % 3 == 0L) 55.0 else 62.0) }
        assertThat(HrvVariation.of(long, day)!!.range).isNotNull()
    }
}
