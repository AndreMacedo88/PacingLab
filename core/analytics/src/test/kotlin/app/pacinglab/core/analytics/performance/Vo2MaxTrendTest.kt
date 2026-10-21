package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.sin

class Vo2MaxTrendTest {
    private val start = LocalDate.of(2026, 8, 1)

    @Test
    fun `steady estimates give a steady trend whose uncertainty shrinks`() {
        val points = (0 until 60 step 3).map { Vo2MaxPoint(start.plusDays(it.toLong()), 55.0, Vo2MaxSource.PACINGLAB) }

        val trend = Vo2MaxTrend.of(points, until = start.plusDays(57))!!

        trend.days.forEach { assertThat(it.value).isCloseTo(55.0, 1e-6) }
        // Twenty estimates pin it down far better than one (prior scatter 3 ml/kg/min).
        assertThat(trend.days.last().sd).isLessThan(1.5)
        assertThat(trend.current.value).isCloseTo(55.0, 1e-6)
    }

    @Test
    fun `uncertainty grows on days without estimates`() {
        val points = listOf(Vo2MaxPoint(start, 52.0, Vo2MaxSource.FITBIT), Vo2MaxPoint(start.plusDays(1), 53.0, Vo2MaxSource.FITBIT))

        val soon = Vo2MaxTrend.of(points, until = start.plusDays(2))!!.current
        val later = Vo2MaxTrend.of(points, until = start.plusDays(60))!!.current

        assertThat(later.value).isCloseTo(soon.value, 1e-9)
        assertThat(later.sd).isGreaterThan(soon.sd)
    }

    @Test
    fun `a rise in fitness is followed gradually, not jumped to`() {
        val points = (0 until 90 step 2).map { d ->
            Vo2MaxPoint(start.plusDays(d.toLong()), if (d < 45) 52.0 else 56.0, Vo2MaxSource.PACINGLAB)
        }

        val trend = Vo2MaxTrend.of(points, until = start.plusDays(88))!!

        val atStep = trend.days.first { it.date == start.plusDays(45) }.value
        assertThat(atStep).isBetween(52.5, 55.5)
        assertThat(trend.days.last().value).isCloseTo(56.0, 0.5)
    }

    @Test
    fun `the scatter of each source is learned and the steadier one pulls harder`() {
        val steady = (0 until 120 step 4).map { Vo2MaxPoint(start.plusDays(it.toLong()), 54.0 + 0.3 * sin(it * 0.7), Vo2MaxSource.FITBIT) }
        val noisy = (2 until 120 step 4).map {
            Vo2MaxPoint(start.plusDays(it.toLong()), 54.0 + 4.0 * sin(it * 1.3), Vo2MaxSource.PACINGLAB)
        }

        val trend = Vo2MaxTrend.of(steady + noisy, until = start.plusDays(119))!!

        assertThat(trend.sourceSd.getValue(Vo2MaxSource.PACINGLAB)).isGreaterThan(trend.sourceSd.getValue(Vo2MaxSource.FITBIT))
        trend.days.forEach { assertThat(it.value).isCloseTo(54.0, 1.0) }
        assertThat(trend.sourceSd.keys).isEqualTo(setOf(Vo2MaxSource.FITBIT, Vo2MaxSource.PACINGLAB))
    }

    @Test
    fun `no estimates give no trend and later ones are ignored`() {
        assertThat(Vo2MaxTrend.of(emptyList(), until = start)).isNull()
        assertThat(Vo2MaxTrend.of(listOf(Vo2MaxPoint(start.plusDays(5), 50.0, Vo2MaxSource.TEST)), until = start)).isNull()
    }

    @Test
    fun `change is read from the smoothed trend`() {
        val points = (0..40).map { Vo2MaxPoint(start.plusDays(it.toLong()), 50.0 + it * 0.1, Vo2MaxSource.FITBIT) }

        val trend = Vo2MaxTrend.of(points, until = start.plusDays(40))!!

        assertThat(trend.change(30)!!).isCloseTo(3.0, 0.6)
        assertThat(trend.change(60)).isNull()
    }
}
