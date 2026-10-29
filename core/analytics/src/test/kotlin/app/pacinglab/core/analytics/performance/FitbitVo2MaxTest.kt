package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FitbitVo2MaxTest {
    private val start = LocalDate.of(2026, 9, 1)

    /**
     * A daily series built the way Fitbit's filter builds it: the variance grows by 0.05 a day, and a run
     * measurement z with noise r updates the next day's value.
     */
    private fun series(days: Int, runs: Map<Int, Pair<Double, Double>>): List<FitbitDailyVo2Max> {
        var x = 50.0
        var p = 2.0
        return List(days) { day ->
            if (day > 0) p += FitbitVo2Max.PROCESS_VARIANCE_PER_DAY
            runs[day - 1]?.let { (z, r) ->
                val gain = p / (p + r)
                x += gain * (z - x)
                p *= 1 - gain
            }
            FitbitDailyVo2Max(start.plusDays(day.toLong()), x, p)
        }
    }

    @Test
    fun `each update gives back the run's measurement and its noise`() {
        val daily = series(30, mapOf(5 to (54.0 to 1.5), 20 to (51.0 to 3.0)))

        val updates = FitbitVo2Max.updates(daily)

        assertThat(updates.map { it.date }).containsExactly(start.plusDays(5), start.plusDays(20))
        assertThat(updates[0].value).isCloseTo(54.0, 1e-6)
        assertThat(updates[0].variance).isCloseTo(1.5, 1e-6)
        assertThat(updates[1].value).isCloseTo(51.0, 1e-6)
        assertThat(updates[1].variance).isCloseTo(3.0, 1e-6)
    }

    @Test
    fun `a gap of several days is bridged with the daily growth of the variance`() {
        val daily = series(30, mapOf(10 to (53.0 to 2.0))).filter { it.date != start.plusDays(9) && it.date != start.plusDays(10) }

        val update = FitbitVo2Max.updates(daily).single()

        assertThat(update.value).isCloseTo(53.0, 1e-6)
        assertThat(update.date).isEqualTo(start.plusDays(10))
    }

    @Test
    fun `daily updates replace recorded values and weigh trusted runs more`() {
        val daily = series(40, mapOf(5 to (54.0 to 1.0), 20 to (51.0 to 4.0)))
        val recorded = listOf(
            Vo2MaxPoint(start.plusDays(5), 54.0, Vo2MaxSource.FITBIT),
            // Fitbit's own series passed this one over: no update followed.
            Vo2MaxPoint(start.plusDays(30), 60.0, Vo2MaxSource.FITBIT),
        )

        val points = FitbitVo2Max.points(recorded, daily)

        assertThat(points).hasSize(2)
        assertThat(points.map { it.date }).containsExactly(start.plusDays(5), start.plusDays(20))
        assertThat(points[0].weight).isGreaterThan(points[1].weight)
    }

    @Test
    fun `without the daily series the recorded values stay as they are`() {
        val recorded = listOf(Vo2MaxPoint(start, 52.0, Vo2MaxSource.FITBIT))

        assertThat(FitbitVo2Max.points(recorded, emptyList())).isEqualTo(recorded)
    }
}
