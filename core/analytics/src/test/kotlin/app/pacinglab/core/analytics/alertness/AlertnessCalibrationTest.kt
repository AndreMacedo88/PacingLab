package app.pacinglab.core.analytics.alertness

import app.pacinglab.core.model.ReactionTest
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

class AlertnessCalibrationTest {
    /** Tests spread over the day like alertness from groggy mornings to sharp afternoons. */
    private fun tests(n: Int, sensitivity: Double, noise: Double, seed: Int): Pair<DoubleArray, DoubleArray> {
        val random = Random(seed)
        val x = DoubleArray(n) { -3.0 + 4.0 * random.nextDouble() }
        val y = DoubleArray(n) { ln(3.4) + sensitivity * x[it] + noise * gaussian(random) }
        return x to y
    }

    private fun gaussian(random: Random): Double = (0 until 12).sumOf { random.nextDouble() } - 6.0

    @Test
    fun `without tests the population sensitivity holds`() {
        val none = AlertnessCalibration.fit(DoubleArray(0), DoubleArray(0))
        assertThat(none).isEqualTo(AlertnessCalibration.POPULATION)
        assertThat(none.learning).isTrue()
        assertThat(none.follows).isNull()
        assertThat(none.performance(10.0, 10.0)).isCloseTo(100.0, 1e-9)
    }

    @Test
    fun `a single test sets the rested speed and keeps the population sensitivity`() {
        val one = AlertnessCalibration.fit(doubleArrayOf(-1.0), doubleArrayOf(ln(3.0)))
        assertThat(one.sensitivity).isCloseTo(AlertnessCalibration.PRIOR_SENSITIVITY, 1e-9)
        assertThat(checkNotNull(one.restedSpeed)).isCloseTo(3.0 * kotlin.math.exp(AlertnessCalibration.PRIOR_SENSITIVITY), 1e-9)
    }

    @Test
    fun `tests from a more sensitive athlete move the sensitivity towards theirs`() {
        val (x, y) = tests(40, sensitivity = 0.07, noise = 0.03, seed = 11)
        val fit = AlertnessCalibration.fit(x, y)
        assertThat(fit.sensitivity).isBetween(0.055, 0.085)
        assertThat(fit.sensitivitySd).isLessThan(AlertnessCalibration.PRIOR_SD / 2)
        assertThat(fit.learning).isFalse()
        assertThat(fit.follows).isNotNull().isTrue()
        assertThat(checkNotNull(fit.restedSpeed)).isCloseTo(3.4, 0.1)
    }

    @Test
    fun `tests unrelated to alertness do not follow the model`() {
        var follows = 0
        repeat(20) { seed ->
            val (x, y) = tests(24, sensitivity = 0.0, noise = 0.06, seed = 100 + seed)
            val fit = AlertnessCalibration.fit(x, y)
            assertThat(fit.sensitivity).isLessThan(AlertnessCalibration.PRIOR_SENSITIVITY)
            if (fit.follows == true) follows++
        }
        assertThat(follows).isLessThan(3)
    }

    @Test
    fun `a test's result is its mean response speed, with slow answers as lapses`() {
        val answers = List(30) { if (it < 3) 600 else 250 }
        val result = checkNotNull(ReactionResult.of(ReactionTest(Instant.EPOCH, null, answers, falseStarts = 2)))
        assertThat(result.speed).isCloseTo((3 * 1000.0 / 600 + 27 * 4.0) / 30, 1e-9)
        assertThat(result.lapses).isEqualTo(3)
        assertThat(result.falseStarts).isEqualTo(2)
        assertThat(abs(result.millis - 1000 / result.speed)).isLessThan(1e-9)
        assertThat(ReactionResult.of(ReactionTest(Instant.EPOCH, null, List(10) { 250 }, 0))).isNull()
    }
}
