package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.analytics.load.FormDay
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

class ReadinessTest {
    private val today = LocalDate.of(2026, 11, 20)

    /** Night ending on [date] with the given values; HRV wobbles around [hrv] by a few percent. */
    private fun night(date: LocalDate, hrv: Double? = 60.0, restingHr: Double? = 52.0, hours: Double = 7.5, breathing: Double? = 12.5) =
        Night(
            date = date,
            start = Instant.parse("${date.minusDays(1)}T21:00:00Z"),
            end = Instant.parse("${date}T05:00:00Z"),
            asleepMillis = (hours * 3_600_000).toLong(),
            stageMillis = emptyMap(),
            hrvMs = hrv,
            restingHr = restingHr,
            respiratoryRate = breathing,
        )

    /** [days] ordinary nights up to and including [last], HRV and resting HR varying a little from night to night. */
    private fun usualNights(days: Int, last: LocalDate = today) = (days - 1 downTo 0).map { back ->
        val wobble = sin(back * 1.7)
        night(last.minusDays(back.toLong()), hrv = 60.0 * exp(0.08 * wobble), restingHr = 52.0 + 1.5 * wobble)
    }

    private fun form(form: Double, fitness: Double = 40.0) = FormDay(today, 0.0, fitness, fitness - form, form)

    @Test
    fun `fewer than two weeks of HRV nights leave the score out`() {
        val readiness = Readiness.of(usualNights(10), form(0.0), today)

        assertThat(readiness.score).isNull()
        assertThat(readiness.hrv).isNull()
        assertThat(readiness.baselineNights).isEqualTo(10)
        // Contributors with enough data are still reported.
        assertThat(readiness.restingHr).isNotNull()
        assertThat(readiness.training).isNotNull()
    }

    @Test
    fun `an ordinary night after ordinary weeks is ready`() {
        val readiness = Readiness.of(usualNights(30), form(0.0), today)

        assertThat(readiness.hrv!!.standing).isEqualTo(Standing.NORMAL)
        assertThat(readiness.restingHr!!.standing).isEqualTo(Standing.NORMAL)
        assertThat(readiness.sleep!!.standing).isEqualTo(Standing.NORMAL)
        assertThat(readiness.score!!).isBetween(70, 85)
        assertThat(readiness.level).isEqualTo(ReadinessLevel.READY)
    }

    @Test
    fun `a week of suppressed HRV with a raised resting heart rate lowers readiness`() {
        val ordinary = usualNights(30, last = today.minusDays(7))
        val strained = (6 downTo 0).map { night(today.minusDays(it.toLong()), hrv = 45.0, restingHr = 57.0) }

        val readiness = Readiness.of(ordinary + strained, form(-5.0), today)

        assertThat(readiness.hrv!!.standing).isEqualTo(Standing.BELOW)
        assertThat(readiness.restingHr!!.standing).isEqualTo(Standing.ABOVE)
        assertThat(readiness.score!!).isLessThan(50)
        assertThat(readiness.level).isEqualTo(ReadinessLevel.LOW)
    }

    @Test
    fun `the HRV normal range is half a standard deviation of ln RMSSD around its mean`() {
        val nights = usualNights(20)

        val hrv = Readiness.of(nights, null, today).hrv!!

        val logs = nights.map { ln(it.hrvMs!!) }
        val mean = logs.average()
        val sd = kotlin.math.sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1))
        assertThat(hrv.normalLowMs).isCloseTo(exp(mean - sd / 2), 1e-9)
        assertThat(hrv.normalHighMs).isCloseTo(exp(mean + sd / 2), 1e-9)
        assertThat(hrv.weekAverageMs).isCloseTo(exp(logs.takeLast(7).average()), 1e-9)
    }

    @Test
    fun `a long pause in night wear starts the baseline afresh`() {
        // One night six weeks earlier, by someone else wearing the watch, then a fresh stretch.
        val stray = night(today.minusDays(60), hrv = 30.0, restingHr = 65.0)
        val fresh = usualNights(16)

        val readiness = Readiness.of(listOf(stray) + fresh, null, today)

        assertThat(readiness.baselineNights).isEqualTo(16)
        assertThat(readiness.hrv!!.standing).isEqualTo(Standing.NORMAL)
    }

    @Test
    fun `a short night scores poorly and need stays between seven and nine hours`() {
        val nights = usualNights(29, last = today.minusDays(1)).map { it.copy(asleepMillis = 6 * 3_600_000L) } +
            night(today, hours = 4.5)

        val sleep = Readiness.of(nights, null, today).sleep!!

        assertThat(sleep.needMillis).isEqualTo(7 * 3_600_000L)
        assertThat(sleep.lastNight.rating).isEqualTo(SleepRating.POOR)
        assertThat(sleep.standing).isEqualTo(Standing.BELOW)
        assertThat(sleep.score).isLessThan(70)
    }

    @Test
    fun `form beyond a band that grows with fitness marks strain or freshness`() {
        val nights = usualNights(20)

        assertThat(Readiness.of(nights, form(-15.0, fitness = 20.0), today).training!!.standing).isEqualTo(Standing.BELOW)
        assertThat(Readiness.of(nights, form(-15.0, fitness = 60.0), today).training!!.standing).isEqualTo(Standing.NORMAL)
        assertThat(Readiness.of(nights, form(12.0, fitness = 20.0), today).training!!.standing).isEqualTo(Standing.ABOVE)
    }

    @Test
    fun `raised breathing with suppressed HRV caps readiness`() {
        val ordinary = usualNights(30, last = today.minusDays(1))
        val ill = night(today, hrv = 40.0, restingHr = 58.0, breathing = 14.5)

        val readiness = Readiness.of(ordinary + ill, form(5.0), today)

        assertThat(readiness.breathingElevated).isTrue()
        assertThat(readiness.score!!).isLessThan(41)
    }

    @Test
    fun `without a night ending today there is no last-night value`() {
        val readiness = Readiness.of(usualNights(20, last = today.minusDays(1)), null, today)

        assertThat(readiness.hrv!!.lastNightMs).isNull()
        assertThat(readiness.sleep).isNull()
        assertThat(readiness.restingHr).isNull()
        assertThat(readiness.breathingElevated).isFalse()
        assertThat(readiness.score!!).isGreaterThan(0)
    }
}
