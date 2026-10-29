package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.pow

class MarathonShapeTest {
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun `targets follow Runalyze's formulas`() {
        val shape = MarathonShapes.of(emptyList(), vo2max = 50.0, until = today)

        assertThat(shape.targetWeeklyKm).isCloseTo(50.0.pow(1.135), 1e-9)
        assertThat(shape.longRunKm).isCloseTo(12 * ln(12.5), 1e-9)
        assertThat(shape.percent).isEqualTo(0.0)
    }

    @Test
    fun `the target week and a target long run every week make 100 percent`() {
        val vo2max = 50.0
        val target = MarathonShapes.of(emptyList(), vo2max, today)
        // Half a year of the target weekly distance: a long run each week and six easy runs under 13 km.
        val runs = (0 until 182 step 7).flatMap { daysAgo ->
            val longRun = RunDistance(today.minusDays(daysAgo.toLong()), target.longRunKm * 1_000)
            val easy = (1..6).map {
                RunDistance(
                    today.minusDays(daysAgo.toLong() + it),
                    (target.targetWeeklyKm - target.longRunKm) / 6 * 1_000,
                )
            }
            easy + longRun
        }

        val shape = MarathonShapes.of(runs, vo2max, today)

        assertThat(shape.weeklyKm).isCloseTo(target.targetWeeklyKm, 1.0)
        // Ten long runs in the last 70 days, weighted 2 down to 0.2: 11 points, 1.1 a week.
        assertThat(shape.longRuns).isEqualTo(10)
        assertThat(shape.longRunShare).isCloseTo(1.1, 1e-9)
        assertThat(shape.percent).isCloseTo(100.0, 5.0)
    }

    @Test
    fun `runs up to 13 km earn no long run points`() {
        val runs = (0 until 70 step 2).map { RunDistance(today.minusDays(it.toLong()), 13_000.0) }

        val shape = MarathonShapes.of(runs, vo2max = 55.0, until = today)

        assertThat(shape.longRuns).isEqualTo(0)
        assertThat(shape.longRunShare).isEqualTo(0.0)
    }

    @Test
    fun `a short history is averaged over at least 70 days`() {
        val runs = listOf(RunDistance(today.minusDays(3), 70_000.0))

        val shape = MarathonShapes.of(runs, vo2max = 50.0, until = today)

        assertThat(shape.weeklyDays).isEqualTo(70)
        assertThat(shape.weeklyKm).isCloseTo(7.0, 1e-9)
    }

    @Test
    fun `a race asks for distance in km to the power 1_23`() {
        assertThat(MarathonShapes.requiredFor(5_000.0)).isCloseTo(7.2, 0.1)
        assertThat(MarathonShapes.requiredFor(21_097.5)).isCloseTo(42.5, 0.5)
        assertThat(MarathonShapes.requiredFor(42_195.0)).isCloseTo(99.6, 0.5)
    }

    @Test
    fun `a short shape slows long races only`() {
        assertThat(MarathonShapes.factor(5_000.0, shapePercent = 20.0)).isEqualTo(1.0)
        assertThat(MarathonShapes.factor(42_195.0, shapePercent = 0.0)).isCloseTo(0.6 + 0.4 * 0.004, 0.01)
        assertThat(MarathonShapes.factor(42_195.0, shapePercent = 100.0)).isEqualTo(1.0)

        val potential = RacePredictions.secondsFromVo2Max(55.0, 42_195.0)!!
        val shaped = RacePredictions.of(55.0, null, MarathonShapes.of(emptyList(), 55.0, today)).last()
        assertThat(shaped.potential!!).isCloseTo(potential, 1e-6)
        assertThat(shaped.fromVo2Max!!).isGreaterThan(potential)
        assertThat(RacePredictions.of(55.0, null).last().fromVo2Max!!).isLessThan(shaped.fromVo2Max)
    }
}
