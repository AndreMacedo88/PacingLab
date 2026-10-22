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
    fun `only an excellent night scores 100`() {
        val excellent = night(day, latency = 8, waso = 4, awakenings = 0)

        val score = SleepScore.of(excellent, needMillis = 7 * hour, earlier = usualWeek(day))

        assertThat(score.score).isEqualTo(100)
        assertThat(score.rating).isEqualTo(SleepRating.GOOD)
    }

    @Test
    fun `a night just meeting every threshold scores about 80`() {
        // Efficiency 85 %, 20 min awake after falling asleep, 30 min to fall asleep, one long awakening,
        // asleep exactly the need and the midpoint 45 min from usual (35 min later bedtime plus 20 min
        // longer to fall asleep than on the usual nights).
        val inBed = 8.0
        val adequate = night(day, inBed = inBed, latency = 30, waso = 42, awakenings = 1, bedtimeShiftMinutes = 35)
            .let {
                it.copy(
                    continuity = it.continuity!!.copy(wakeAfterOnsetMillis = 20 * 60_000L),
                    asleepMillis = (inBed * 0.85 * hour).toLong(),
                )
            }

        val score = SleepScore.of(adequate, needMillis = adequate.asleepMillis, earlier = usualWeek(day))

        assertThat(score.continuity!!).isCloseTo(0.6, 1e-9)
        assertThat(score.regularity!!).isCloseTo(0.6, 1e-9)
        assertThat(score.score).isBetween(78, 92)
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
    fun `measures are graded around their threshold`() {
        // Higher is better: efficiency.
        assertThat(SleepScore.graded(0.97, best = 0.95, threshold = 0.85, worst = 0.75)).isEqualTo(1.0)
        assertThat(SleepScore.graded(0.90, best = 0.95, threshold = 0.85, worst = 0.75)).isCloseTo(0.8, 1e-9)
        assertThat(SleepScore.graded(0.85, best = 0.95, threshold = 0.85, worst = 0.75)).isCloseTo(0.6, 1e-9)
        assertThat(SleepScore.graded(0.80, best = 0.95, threshold = 0.85, worst = 0.75)).isCloseTo(0.3, 1e-9)
        assertThat(SleepScore.graded(0.70, best = 0.95, threshold = 0.85, worst = 0.75)).isEqualTo(0.0)
        // Lower is better: minutes awake after falling asleep.
        assertThat(SleepScore.graded(11.0, best = 5.0, threshold = 20.0, worst = 50.0)).isCloseTo(0.84, 1e-9)
        assertThat(SleepScore.graded(35.0, best = 5.0, threshold = 20.0, worst = 50.0)).isCloseTo(0.3, 1e-9)
        assertThat(SleepScore.graded(60.0, best = 5.0, threshold = 20.0, worst = 50.0)).isEqualTo(0.0)
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
        val usual = SleepScore.of(night(day), needMillis = 7 * hour, earlier = usualWeek(day)).score

        assertThat(
            SleepScore.of(night(day, deep = 0.08, rem = 0.10), needMillis = 7 * hour, earlier = usualWeek(day)).score,
        ).isEqualTo(usual)
    }

    @Test
    fun `regularity ignores nights more than two weeks back`() {
        val old = (30 downTo 24).map { night(day.minusDays(it.toLong()), bedtimeShiftMinutes = 180) }

        assertThat(SleepScore.of(night(day), needMillis = 7 * hour, earlier = old).regularity).isNull()
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

    @Test
    fun `a fixed need replaces the usual sleep for every night`() {
        val nights = (0..10).map { night(day.minusDays(it.toLong()), inBed = 7.2) }

        val automatic = SleepHistory.of(nights)
        val fixed = SleepHistory.of(nights, fixedNeedMillis = 9 * hour)

        assertThat(fixed.needs.values.toSet()).isEqualTo(setOf(9 * hour))
        assertThat(fixed.scores.getValue(day).score).isLessThan(automatic.scores.getValue(day).score)
    }
}
