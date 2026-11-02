package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
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
     * A night ending on [date] after [hours] from falling asleep to the final awakening, lying awake
     * [waso] minutes in [awakenings] long awakenings; [deep] and [rem] as shares of sleep.
     */
    private fun night(
        date: LocalDate,
        hours: Double = 8.0,
        waso: Long = 10,
        awakenings: Int = 1,
        deep: Double = 0.18,
        rem: Double = 0.22,
        bedtimeShiftMinutes: Long = 0,
    ): Night {
        val end = Instant.parse("${date}T06:00:00Z").plus(Duration.ofMinutes(bedtimeShiftMinutes))
        val start = end.minusMillis((hours * hour).toLong())
        val asleep = (hours * hour).toLong() - waso * 60_000
        return Night(
            date = date,
            start = start,
            end = end,
            asleepMillis = asleep,
            stageMillis = mapOf(
                SleepStageType.DEEP to (asleep * deep).toLong(),
                SleepStageType.REM to (asleep * rem).toLong(),
                SleepStageType.LIGHT to (asleep * (1 - deep - rem)).toLong(),
                SleepStageType.AWAKE to waso * 60_000,
            ),
            hrvMs = null,
            restingHr = null,
            respiratoryRate = null,
            continuity = SleepContinuity(waso * 60_000, awakenings),
            zone = cet,
        )
    }

    @Test
    fun `only an excellent night scores 100`() {
        val excellent = night(day, waso = 4, awakenings = 0)

        val score = SleepScore.of(excellent, needMillis = 7 * hour)

        assertThat(score.score).isEqualTo(100)
        assertThat(score.rating).isEqualTo(SleepRating.GOOD)
    }

    @Test
    fun `a night just meeting every threshold earns 60 percent of each part`() {
        // 20 min awake after falling asleep, one long awakening, asleep 85 % of the need.
        val adequate = night(day, waso = 20, awakenings = 1)

        val score = SleepScore.of(adequate, needMillis = (adequate.asleepMillis / 0.85).toLong())

        assertThat(score.continuity!!).isCloseTo(0.6, 1e-9)
        assertThat(score.duration).isCloseTo(0.6, 1e-6)
        assertThat(score.score).isEqualTo(60)
    }

    @Test
    fun `duration weighs more than continuity`() {
        val short = night(day, hours = 6.0, waso = 4, awakenings = 0)
        val broken = night(day, hours = 9.3, waso = 45, awakenings = 3)

        val shortScore = SleepScore.of(short, needMillis = 8 * hour)
        val brokenScore = SleepScore.of(broken, needMillis = 8 * hour)

        assertThat(shortScore.continuity!!).isEqualTo(1.0)
        assertThat(brokenScore.duration).isEqualTo(1.0)
        assertThat(shortScore.score).isLessThan(brokenScore.score)
    }

    @Test
    fun `a short broken night scores poorly`() {
        val bad = night(day, hours = 5.5, waso = 60, awakenings = 5, deep = 0.06, rem = 0.12)

        val score = SleepScore.of(bad, needMillis = 8 * hour)

        assertThat(score.score).isLessThan(30)
        assertThat(score.rating).isEqualTo(SleepRating.POOR)
    }

    @Test
    fun `measures are graded around their threshold`() {
        // Higher is better.
        assertThat(Grading.graded(0.97, best = 0.95, threshold = 0.85, worst = 0.75)).isEqualTo(1.0)
        assertThat(Grading.graded(0.90, best = 0.95, threshold = 0.85, worst = 0.75)).isCloseTo(0.8, 1e-9)
        assertThat(Grading.graded(0.85, best = 0.95, threshold = 0.85, worst = 0.75)).isCloseTo(0.6, 1e-9)
        assertThat(Grading.graded(0.80, best = 0.95, threshold = 0.85, worst = 0.75)).isCloseTo(0.3, 1e-9)
        assertThat(Grading.graded(0.70, best = 0.95, threshold = 0.85, worst = 0.75)).isEqualTo(0.0)
        // Lower is better: minutes awake after falling asleep.
        assertThat(Grading.graded(11.0, best = 5.0, threshold = 20.0, worst = 50.0)).isCloseTo(0.84, 1e-9)
        assertThat(Grading.graded(35.0, best = 5.0, threshold = 20.0, worst = 50.0)).isCloseTo(0.3, 1e-9)
        assertThat(Grading.graded(60.0, best = 5.0, threshold = 20.0, worst = 50.0)).isEqualTo(0.0)
    }

    @Test
    fun `time awake in bed before falling asleep and after the final awakening does not count`() {
        fun record(before: Long, after: Long): SleepSession {
            val start = Instant.parse("2026-11-19T22:00:00Z")
            var t = start
            val stages = listOf(
                SleepStageType.AWAKE to before,
                SleepStageType.LIGHT to 240L,
                SleepStageType.AWAKE to 12L,
                SleepStageType.REM to 228L,
                SleepStageType.AWAKE to after,
            ).filter {
                it.second > 0
            }.map { (type, minutes) -> SleepStage(t, t.plus(Duration.ofMinutes(minutes)), type).also { t = it.end } }
            return SleepSession("n", start, t, cet, "com.fitbit.FitbitMobile", stages)
        }
        fun score(session: SleepSession) =
            SleepScore.of(Nights.of(listOf(session), emptyList(), emptyList(), emptyList(), cet).single(), needMillis = 8 * hour)

        // An hour of reading before sleep and forty minutes in bed after waking.
        val reading = score(record(before = 60, after = 40))

        assertThat(reading).isEqualTo(score(record(before = 0, after = 0)))
        assertThat(reading.measures.map { it.kind })
            .isEqualTo(listOf(SleepMeasureKind.DURATION, SleepMeasureKind.AWAKE_AFTER_ONSET, SleepMeasureKind.LONG_AWAKENINGS))
    }

    @Test
    fun `without stages only duration counts`() {
        val plain = night(day).copy(stageMillis = emptyMap(), continuity = null)

        val score = SleepScore.of(plain, needMillis = 7 * hour)

        assertThat(score.continuity).isNull()
        assertThat(score.score).isEqualTo(100)
    }

    @Test
    fun `stage shares do not change the score`() {
        val usual = SleepScore.of(night(day), needMillis = 7 * hour).score

        assertThat(SleepScore.of(night(day, deep = 0.08, rem = 0.10), needMillis = 7 * hour).score).isEqualTo(usual)
    }

    @Test
    fun `the time of night does not change the score`() {
        val usual = SleepScore.of(night(day), needMillis = 7 * hour).score

        assertThat(SleepScore.of(night(day, bedtimeShiftMinutes = 240), needMillis = 7 * hour).score).isEqualTo(usual)
    }

    @Test
    fun `need is the usual sleep of the last 30 nights kept between seven and nine hours`() {
        val short = (1..10).map { night(day.minusDays(it.toLong()), hours = 6.0) }
        val long = (1..10).map { night(day.minusDays(it.toLong()), hours = 10.5) }
        val usual = (1..10).map { night(day.minusDays(it.toLong()), hours = 8.1) }

        assertThat(SleepScore.need(short, day)).isEqualTo(7 * hour)
        assertThat(SleepScore.need(long, day)).isEqualTo(9 * hour)
        assertThat(SleepScore.need(usual, day)).isBetween(7 * hour + 50 * 60_000, 8 * hour)
    }

    @Test
    fun `a fixed need replaces the usual sleep for every night`() {
        val nights = (0..10).map { night(day.minusDays(it.toLong()), hours = 7.2) }

        val automatic = SleepHistory.of(nights)
        val fixed = SleepHistory.of(nights, fixedNeedMillis = 9 * hour)

        assertThat(fixed.needs.values.toSet()).isEqualTo(setOf(9 * hour))
        assertThat(fixed.scores.getValue(day).score).isLessThan(automatic.scores.getValue(day).score)
    }
}
