package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.SleepStageType
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepScoreTest {
    private val cet = ZoneOffset.ofHours(1)
    private val day = LocalDate.of(2026, 11, 20)
    private val hour = 3_600_000L

    /**
     * A night ending on [date] after [inBed] hours in bed, falling asleep after [latency] minutes and
     * lying awake [waso] minutes in [awakenings] long awakenings; [deep] and [rem] as shares of sleep.
     */
    private fun night(
        date: LocalDate,
        inBed: Double = 8.0,
        latency: Long = 10,
        waso: Long = 10,
        awakenings: Int = 1,
        deep: Double = 0.18,
        rem: Double = 0.22,
        bedtimeShiftMinutes: Long = 0,
    ): Night {
        val end = Instant.parse("${date}T06:00:00Z").plus(Duration.ofMinutes(bedtimeShiftMinutes))
        val start = end.minusMillis((inBed * hour).toLong())
        val asleep = (inBed * hour).toLong() - (latency + waso) * 60_000
        val onset = start.plus(Duration.ofMinutes(latency))
        return Night(
            date = date,
            start = start,
            end = end,
            asleepMillis = asleep,
            stageMillis = mapOf(
                SleepStageType.DEEP to (asleep * deep).toLong(),
                SleepStageType.REM to (asleep * rem).toLong(),
                SleepStageType.LIGHT to (asleep * (1 - deep - rem)).toLong(),
                SleepStageType.AWAKE to (latency + waso) * 60_000,
            ),
            hrvMs = null,
            restingHr = null,
            respiratoryRate = null,
            continuity = SleepContinuity(onset, end, latency * 60_000, waso * 60_000, awakenings),
            zone = cet,
        )
    }

    private fun usualWeek(last: LocalDate) = (7 downTo 1).map { night(last.minusDays(it.toLong())) }

    @Test
    fun `a night meeting every criterion scores 100`() {
        val score = SleepScore.of(night(day), needMillis = 7 * hour, earlier = usualWeek(day))

        assertThat(score.score).isEqualTo(100)
        assertThat(score.rating).isEqualTo(SleepRating.GOOD)
    }

    @Test
    fun `a short broken night with little deep sleep scores poorly`() {
        val bad = night(day, inBed = 5.5, latency = 55, waso = 60, awakenings = 5, deep = 0.06, rem = 0.12, bedtimeShiftMinutes = 150)

        val score = SleepScore.of(bad, needMillis = 8 * hour, earlier = usualWeek(day))

        assertThat(score.score).isLessThan(30)
        assertThat(score.rating).isEqualTo(SleepRating.POOR)
        assertThat(score.regularity!!).isEqualTo(0.0)
    }

    @Test
    fun `each criterion scales linearly between good and poor`() {
        assertThat(SleepScore.fraction(0.85, good = 0.85, bad = 0.74)).isEqualTo(1.0)
        assertThat(SleepScore.fraction(0.795, good = 0.85, bad = 0.74)).isCloseTo(0.5, 1e-9)
        assertThat(SleepScore.fraction(35.5, good = 20.0, bad = 51.0)).isCloseTo(0.5, 1e-9)
        assertThat(SleepScore.fraction(70.0, good = 20.0, bad = 51.0)).isEqualTo(0.0)
    }

    @Test
    fun `without stages or earlier nights only the measurable parts count`() {
        val plain = night(day).copy(stageMillis = emptyMap(), continuity = null)

        val score = SleepScore.of(plain, needMillis = 7 * hour, earlier = emptyList())

        assertThat(score.continuity).isNull()
        assertThat(score.regularity).isNull()
        assertThat(score.score).isEqualTo(100)
    }

    @Test
    fun `stage shares do not change the score`() {
        val lowRem = night(day, deep = 0.08, rem = 0.10)

        assertThat(SleepScore.of(lowRem, needMillis = 7 * hour, earlier = usualWeek(day)).score).isEqualTo(100)
    }

    @Test
    fun `timing counts minutes outside 02 00 to 04 00 around the clock`() {
        assertThat(SleepScore.minutesOutsideWindow(3 * 60)).isEqualTo(0)
        assertThat(SleepScore.minutesOutsideWindow(5 * 60)).isEqualTo(60)
        assertThat(SleepScore.minutesOutsideWindow(60)).isEqualTo(60)
        assertThat(SleepScore.minutesOutsideWindow(23 * 60)).isEqualTo(180)
    }

    @Test
    fun `regularity compares clock times across midnight`() {
        // Usual midpoint around 02:00 local; this night's midpoint around 23:30 the evening before.
        assertThat(SleepScore.circularDistance(23 * 60 + 30, 2 * 60)).isEqualTo(150)
        assertThat(SleepScore.circularMedianMinute(listOf(23 * 60 + 50, 10, 30))).isEqualTo(10)
    }

    @Test
    fun `need is the usual sleep of the last 30 nights kept between seven and nine hours`() {
        val short = (1..10).map { night(day.minusDays(it.toLong()), inBed = 6.0) }
        val long = (1..10).map { night(day.minusDays(it.toLong()), inBed = 10.5) }
        val usual = (1..10).map { night(day.minusDays(it.toLong()), inBed = 8.3) }

        assertThat(SleepScore.need(short, day)).isEqualTo(7 * hour)
        assertThat(SleepScore.need(long, day)).isEqualTo(9 * hour)
        assertThat(SleepScore.need(usual, day)).isBetween(7 * hour + 50 * 60_000, 8 * hour)
    }
}
