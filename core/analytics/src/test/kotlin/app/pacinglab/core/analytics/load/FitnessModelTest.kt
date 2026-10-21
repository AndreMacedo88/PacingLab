package app.pacinglab.core.analytics.load

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.exp

class FitnessModelTest {
    private val day = LocalDate.of(2026, 10, 1)

    @Test
    fun `hand-computed days`() {
        // Seed = mean of the four days = 37.5; rates 1 - e^(-1/42) and 1 - e^(-1/7).
        val series = FitnessModel.series(mapOf(day to 100.0, day.plusDays(2) to 50.0), until = day.plusDays(3))

        assertThat(series.map { it.date }).isEqualTo(List(4) { day.plusDays(it.toLong()) })
        assertThat(series.map { it.load }).isEqualTo(listOf(100.0, 0.0, 50.0, 0.0))
        val expected = listOf(
            Triple(38.970520, 45.820131, 0.0),
            Triple(38.053609, 39.720459, -6.849612),
            Triple(38.334687, 41.088893, -1.666850),
            Triple(37.432737, 35.619053, -2.754206),
        )
        series.zip(expected).forEach { (actual, wanted) ->
            assertThat(actual.fitness).isCloseTo(wanted.first, 1e-6)
            assertThat(actual.fatigue).isCloseTo(wanted.second, 1e-6)
            assertThat(actual.form).isCloseTo(wanted.third, 1e-6)
        }
    }

    @Test
    fun `steady training keeps fitness and fatigue at the daily load`() {
        val loads = (0L until 120L).associate { day.plusDays(it) to 60.0 }

        val series = FitnessModel.series(loads, until = day.plusDays(119))

        series.forEach {
            assertThat(it.fitness).isCloseTo(60.0, 1e-9)
            assertThat(it.fatigue).isCloseTo(60.0, 1e-9)
            assertThat(it.form).isCloseTo(0.0, 1e-9)
        }
    }

    @Test
    fun `rest days decay fitness by a constant factor`() {
        val series = FitnessModel.series(mapOf(day to 500.0), until = day.plusDays(60))

        for (i in 1..60) {
            assertThat(series[i].fitness / series[i - 1].fitness).isCloseTo(exp(-1.0 / 42), 1e-12)
            assertThat(series[i].fatigue / series[i - 1].fatigue).isCloseTo(exp(-1.0 / 7), 1e-12)
        }
    }

    @Test
    fun `form is the previous day's fitness minus fatigue`() {
        val loads = mapOf(day to 80.0, day.plusDays(1) to 120.0, day.plusDays(4) to 40.0)

        val series = FitnessModel.series(loads, until = day.plusDays(10))

        for (i in 1 until series.size) {
            assertThat(series[i].form).isCloseTo(series[i - 1].fitness - series[i - 1].fatigue, 1e-12)
        }
    }

    @Test
    fun `no loads or an end before the first load give no days`() {
        assertThat(FitnessModel.series(emptyMap(), until = day)).isEmpty()
        assertThat(FitnessModel.series(mapOf(day to 50.0), until = day.minusDays(1))).isEmpty()
    }

    @Test
    fun `ramp rate is the change in fitness over a week`() {
        val series = FitnessModel.series((0L until 14L).associate { day.plusDays(it) to it * 10.0 }, until = day.plusDays(13))

        assertThat(FitnessModel.rampRate(series, 6)).isNull()
        assertThat(FitnessModel.rampRate(series, 13)!!).isCloseTo(series[13].fitness - series[6].fitness, 1e-12)
    }

    @Test
    fun `monotony and strain follow Foster`() {
        val week = WeekStrain.of(doubleArrayOf(100.0, 0.0, 100.0, 0.0, 100.0, 0.0, 0.0))!!

        assertThat(week.load).isEqualTo(300.0)
        assertThat(week.monotony).isCloseTo(0.866025, 1e-6)
        assertThat(week.strain).isCloseTo(259.807621, 1e-6)
        assertThat(WeekStrain.of(DoubleArray(7))).isNull()
    }
}
