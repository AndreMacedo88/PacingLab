package app.pacinglab.core.analytics.recovery

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class SleepTimingTest {
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    private val until = LocalDate.of(2026, 10, 25) // a Sunday

    /** A night ending on the morning of [date], from [bed] the evening or night before until [wake]. */
    private fun night(date: LocalDate, bed: LocalTime, wake: LocalTime): Night {
        val start = (if (bed.hour >= 12) date.minusDays(1) else date).atTime(bed).toInstant(ZoneOffset.UTC)
        val end = date.atTime(wake).toInstant(ZoneOffset.UTC)
        val millis = Duration.between(start, end).toMillis()
        return Night(date, start, end, millis, emptyMap(), hrvMs = null, restingHr = null, respiratoryRate = null, zone = ZoneOffset.UTC)
    }

    /** Four weeks: 23:00 to 06:30 before workdays, 00:30 to 09:00 before the weekend. */
    private val nights = (0 until 28).map { back ->
        val date = until.minusDays(back.toLong())
        if (date.dayOfWeek in
            weekend
        ) {
            night(date, LocalTime.of(0, 30), LocalTime.of(9, 0))
        } else {
            night(date, LocalTime.of(23, 0), LocalTime.of(6, 30))
        }
    }

    @Test
    fun `mid-sleep on free days is corrected for the workdays' sleep debt, and social jetlag is the gap`() {
        val timing = SleepTiming.of(nights, until, weekend)!!

        // Workdays: 7:30 around 02:45. Free days: 8:30 around 04:45, minus half the extra over the week's average.
        assertThat(timing.work!!.midpointClock).isEqualTo(2 * 60 + 45)
        assertThat(timing.free!!.midpointClock).isEqualTo(4 * 60 + 45)
        assertThat(timing.chronotype).isEqualTo(4 * 60 + 24)
        assertThat(timing.chronotypeKind).isEqualTo(Chronotype.INTERMEDIATE)
        assertThat(timing.socialJetlagMinutes).isEqualTo(120)
        assertThat(timing.socialJetlag).isEqualTo(SocialJetlagLevel.HIGH)
    }

    @Test
    fun `which days are free is the athlete's to say`() {
        val shifted = SleepTiming.of(nights, until, setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))!!

        // Now the late weekend nights count as workdays and pull that average later.
        assertThat(shifted.free!!.midpointClock).isEqualTo(2 * 60 + 45)
        assertThat(shifted.socialJetlagMinutes!! < 60).isEqualTo(true)
    }

    @Test
    fun `nothing is said before both kinds of night are known`() {
        val oneFreeNight = nights.filter { it.date.dayOfWeek !in weekend } + nights.first { it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val timing = SleepTiming.of(oneFreeNight, until, weekend)!!

        assertThat(timing.ready).isFalse()
        assertThat(timing.chronotype).isNull()
        assertThat(SleepTiming.of(emptyList(), until, weekend)).isNull()
    }

    @Test
    fun `mid-sleep before midnight counts as early`() {
        val early = SleepTiming(null, null, chronotype = 23 * 60 + 30, socialJetlagMinutes = 10)

        assertThat(early.chronotypeKind).isEqualTo(Chronotype.EARLY)
        assertThat(early.socialJetlag).isEqualTo(SocialJetlagLevel.LOW)
    }
}
