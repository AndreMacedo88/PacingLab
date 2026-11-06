package app.pacinglab.core.analytics.alertness

import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.model.ReactionTest
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isIn
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.random.Random

class AlertnessTest {
    private val zone = ZoneOffset.ofHours(1)
    private val today = LocalDate.of(2026, 1, 30)
    private val now = today.atTime(12, 0).atZone(zone).toInstant()

    private fun at(date: LocalDate, time: String): Instant = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant()

    /** A night ending on the morning of [date], from [bed] the evening before to [wake]. */
    private fun sleep(date: LocalDate, bed: String = "23:00", wake: String = "07:00"): Pair<Night, SleepSession> {
        val start = if (LocalTime.parse(bed) > LocalTime.NOON) at(date.minusDays(1), bed) else at(date, bed)
        val end = at(date, wake)
        val night = Night(
            date = date,
            start = start,
            end = end,
            asleepMillis = Duration.between(start, end).toMillis(),
            stageMillis = emptyMap(),
            hrvMs = null,
            restingHr = null,
            respiratoryRate = null,
            zone = zone,
        )
        return night to SleepSession("sleep-$date", start, end, zone, "watch", listOf(SleepStage(start, end, SleepStageType.LIGHT)))
    }

    private fun regular(days: Int = 45, change: (LocalDate) -> Pair<Night, SleepSession>? = { sleep(it) }) =
        (days - 1 downTo 0).mapNotNull { change(today.minusDays(it.toLong())) }

    private fun alertness(nights: List<Pair<Night, SleepSession>>, tests: List<ReactionTest> = emptyList(), chronotype: Int? = null) =
        Alertness.of(nights.map { it.second }, SleepHistory.of(nights.map { it.first }), chronotype, tests, zone, now)

    @Test
    fun `a regular full night gives a rested day, sharpest in the afternoon`() {
        val history = alertness(regular())
        val day = checkNotNull(history.days[today])
        assertThat(day.score).isCloseTo(100.0, 0.5)
        assertThat(day.level).isEqualTo(AlertnessLevel.ALERT)
        assertThat(day.assumed).isFalse()
        assertThat(day.wake).isEqualTo(at(today, "07:00"))
        assertThat(day.end).isEqualTo(at(today, "23:00"))
        val (start, end) = checkNotNull(day.best)
        assertThat(start.atZone(zone).hour).isBetween(13, 17)
        assertThat(Duration.between(start, end)).isEqualTo(Duration.ofHours(3))
        assertThat(checkNotNull(day.at(at(today, "07:05")))).isLessThan(90.0)
        assertThat(history.days.size).isEqualTo(Alertness.DAYS)
    }

    @Test
    fun `a short night lowers the day it starts and a little of the next`() {
        val history = alertness(regular { if (it == today.minusDays(1)) sleep(it, bed = "02:00") else sleep(it) })
        val short = checkNotNull(history.days[today.minusDays(1)])
        val after = checkNotNull(history.days[today])
        assertThat(short.score).isLessThan(97.0)
        assertThat(short.level).isIn(AlertnessLevel.REDUCED, AlertnessLevel.LOW)
        assertThat(after.score).isBetween(short.score, 100.0)
        assertThat(checkNotNull(history.days[today.minusDays(3)]).score).isCloseTo(100.0, 0.5)
    }

    @Test
    fun `a missing night is assumed to be the usual one`() {
        val history = alertness(regular { if (it == today) null else sleep(it) })
        val day = checkNotNull(history.days[today])
        assertThat(day.assumed).isTrue()
        assertThat(day.wake).isEqualTo(at(today, "07:00"))
        assertThat(day.score).isCloseTo(100.0, 0.5)
    }

    @Test
    fun `without recorded nights there are no days, and a test is due`() {
        val history = alertness(emptyList())
        assertThat(history.days).isEmpty()
        assertThat(history.testDue).isTrue()
        assertThat(history.calibration).isEqualTo(AlertnessCalibration.POPULATION)
    }

    @Test
    fun `a later chronotype moves the circadian peak later`() {
        assertThat(Alertness.phaseShift(null)).isEqualTo(0.0)
        assertThat(Alertness.phaseShift(5 * 60)).isCloseTo(1.0, 1e-12)
        assertThat(Alertness.phaseShift(23 * 60)).isEqualTo(-2.0)
        val early = checkNotNull(alertness(regular(), chronotype = 3 * 60).days[today]?.best).first
        val late = checkNotNull(alertness(regular(), chronotype = 5 * 60).days[today]?.best).first
        assertThat(early).isLessThan(late)
    }

    @Test
    fun `tests teach the athlete's sensitivity, and come less often as they add up`() {
        val nights = regular()
        val model = alertness(nights)
        val random = Random(4)
        val sensitivity = 0.06
        val tests = (1..20).map { day ->
            val date = today.minusDays(day.toLong())
            val start = at(date, "%02d:00".format(8 + (day * 5) % 13))
            val a = checkNotNull(
                model.days[date]?.points?.minBy {
                    kotlin.math.abs(Duration.between(it.time, start.plusSeconds(90)).toMillis())
                },
            ).alertness
            val speed = 3.4 * exp(sensitivity * (a - model.reference) + 0.02 * (random.nextDouble() - 0.5))
            val millis = (1000 / speed).roundToInt()
            ReactionTest(start, zone, List(60) { millis }, falseStarts = 0)
        }
        val history = alertness(nights, tests)
        assertThat(history.calibration.tests).isEqualTo(20)
        assertThat(history.calibration.sensitivity).isBetween(0.045, 0.07)
        assertThat(history.calibration.follows).isNotNull().isTrue()
        for (test in history.tests) assertThat(checkNotNull(test.measured)).isCloseTo(checkNotNull(test.expected), 2.0)
        // 20 tests, the last yesterday: weekly from 12 on.
        assertThat(history.testDue).isFalse()
        val week = tests.map { it.copy(start = it.start.minus(Duration.ofDays(7))) }
        assertThat(alertness(nights, week).testDue).isTrue()
        assertThat(alertness(nights, tests.take(3).map { it.copy(start = it.start.minus(Duration.ofDays(1))) }).testDue).isTrue()
        assertThat(alertness(nights, tests.take(3)).testDue).isFalse()
    }
}
