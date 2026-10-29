package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepHealthTest {
    private val day = LocalDate.of(2026, 11, 20)
    private val hour = 3_600_000L

    /** A night of the morning [date], in bed from [bedtime] (hours after midnight UTC, evening as negative) for [hours]. */
    private fun night(date: LocalDate, bedtime: Double = -1.0, hours: Double = 8.0, awake: Long = 0): Night {
        val start = Instant.parse("${date}T00:00:00Z").plusMillis((bedtime * hour).toLong())
        val end = start.plusMillis((hours * hour).toLong())
        val asleepEnd = end.minus(Duration.ofMinutes(awake))
        return Night(
            date = date,
            start = start,
            end = end,
            asleepMillis = Duration.between(start, asleepEnd).toMillis(),
            stageMillis = emptyMap(),
            hrvMs = null,
            restingHr = null,
            respiratoryRate = null,
            continuity = SleepContinuity(start, asleepEnd, 0, 0, 0),
            zone = ZoneOffset.UTC,
            stages = listOfNotNull(
                SleepStage(start, asleepEnd, SleepStageType.LIGHT),
                if (awake > 0) SleepStage(asleepEnd, end, SleepStageType.AWAKE) else null,
            ),
        )
    }

    private fun needs(nights: List<Night>, need: Long = 8 * hour) = nights.associate { it.date to need }

    @Test
    fun `the same schedule every day has a regularity index of 100`() {
        val nights = (13 downTo 0).map { night(day.minusDays(it.toLong())) }

        assertThat(SleepHealth.regularityIndex(nights)!!).isCloseTo(100.0, 1e-9)
    }

    @Test
    fun `alternating bedtimes lower the regularity index`() {
        // Every other night three hours later: three hours a day differ in the morning and three in the evening.
        val nights = (13 downTo 0).map { night(day.minusDays(it.toLong()), bedtime = if (it % 2 == 0) -1.0 else 2.0) }

        // 6 of 24 hours differ: agreement 0.75, index 200 x 0.75 - 100 = 50.
        assertThat(SleepHealth.regularityIndex(nights)!!).isCloseTo(50.0, 0.5)
    }

    @Test
    fun `regularity needs pairs of consecutive nights`() {
        val everyOther = (12 downTo 0 step 2).map { night(day.minusDays(it.toLong())) }

        assertThat(SleepHealth.regularityIndex(everyOther)).isNull()
    }

    @Test
    fun `regular habits of enough sleep at the usual time score well`() {
        val nights = (13 downTo 0).map { night(day.minusDays(it.toLong()), bedtime = -1.0, hours = 8.0) }

        val health = SleepHealth.of(nights, needs(nights), day)!!

        // Midpoint at 03:00, regularity 100, duration at need, efficiency 100 %.
        assertThat(health.score).isEqualTo(100)
        assertThat(health.usualMidpointMinute).isEqualTo(3 * 60)
    }

    @Test
    fun `late irregular short nights score poorly`() {
        val nights = (13 downTo 0).map {
            night(day.minusDays(it.toLong()), bedtime = if (it % 2 == 0) 4.0 else 6.0, hours = 6.0, awake = 60)
        }

        val health = SleepHealth.of(nights, needs(nights), day)!!

        assertThat(health.score).isLessThan(SleepScore.FAIR_FROM)
        assertThat(health.measure(SleepHealthMeasureKind.TIMING)!!.withinThreshold).isEqualTo(false)
    }

    @Test
    fun `fewer than five nights give no sleep health`() {
        val nights = (3 downTo 0).map { night(day.minusDays(it.toLong())) }

        assertThat(SleepHealth.of(nights, needs(nights), day)).isNull()
    }

    @Test
    fun `history scores every morning with enough nights`() {
        val nights = (20 downTo 0).map { night(day.minusDays(it.toLong())) }

        val history = SleepHistory.of(nights, fixedNeedMillis = 8 * hour)

        assertThat(history.health[day]).isNotNull()
        assertThat(history.health[day.minusDays(20)]).isNull()
        assertThat(history.health.size).isBetween(15, 17)
    }

    @Test
    fun `timing counts minutes outside 02 00 to 04 00 around the clock`() {
        assertThat(SleepHealth.minutesOutsideWindow(3 * 60)).isEqualTo(0)
        assertThat(SleepHealth.minutesOutsideWindow(5 * 60)).isEqualTo(60)
        assertThat(SleepHealth.minutesOutsideWindow(60)).isEqualTo(60)
        assertThat(SleepHealth.minutesOutsideWindow(23 * 60)).isEqualTo(180)
    }

    @Test
    fun `clock times are compared across midnight`() {
        assertThat(SleepHealth.circularDistance(23 * 60 + 30, 2 * 60)).isEqualTo(150)
        assertThat(SleepHealth.circularMedianMinute(listOf(23 * 60 + 50, 10, 30))).isEqualTo(10)
    }
}
