package app.pacinglab.core.analytics.alertness

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * How the athlete's reaction tests follow the model. Each test's log response speed is fitted on the
 * model's alertness at the time of the test, measured from the rested reference R:
 *
 *   ln(speed) = a + b (A - R) + e,  e ~ N(0, sigma^2)
 *
 * with [PRIOR_SENSITIVITY] +- [PRIOR_SD] as the prior of b, so that without tests the score uses the
 * population value and every test moves it towards the athlete's own. a has a flat prior: phones differ
 * too much in touch delay for a population value. sigma is estimated from the residuals together with
 * [PRIOR_WEIGHT] tests' worth of [PRIOR_NOISE].
 *
 * The prior comes from Basner, Mollicone and Dinges (2011): by the morning after a night without sleep,
 * response speed on the 3-minute test had fallen by roughly a quarter (from about 4 per second), while the
 * model's alertness falls by about 8 (from about 13 rested to 5), ln(3/4) / 8 = 0.036 per unit. People
 * differ widely and consistently in how much sleep loss slows them (Van Dongen 2004), which the prior's
 * spread allows for.
 *
 * @property tests the tests fitted.
 * @property sensitivity b: the change in log response speed per unit of alertness; about the share of
 *   speed per unit.
 * @property intercept a: log response speed at the rested reference; null without tests.
 * @property error the left-out root mean squared error of the model on the tests, from [MIN_TESTS] tests.
 * @property averageError the same for the athlete's mean speed, the measure to beat.
 */
data class AlertnessCalibration(
    val tests: Int,
    val sensitivity: Double,
    val sensitivitySd: Double,
    val intercept: Double?,
    val noise: Double,
    val error: Double? = null,
    val averageError: Double? = null,
) {
    /** Fewer tests than [MIN_TESTS]: the sensitivity is still mostly the population's. */
    val learning: Boolean get() = tests < MIN_TESTS

    /** Whether the model predicts the tests clearly better than their average; null while learning. */
    val follows: Boolean?
        get() {
            val model = error ?: return null
            val average = averageError ?: return null
            return model <= CLEARLY * average
        }

    /** Response speed at the rested reference, per second; null without tests. */
    val restedSpeed: Double? get() = intercept?.let(::exp)

    /** Performance at alertness [a] as a percentage of the rested reference [reference]. */
    fun performance(a: Double, reference: Double): Double = 100.0 * exp(sensitivity * (a - reference))

    companion object {
        const val PRIOR_SENSITIVITY = 0.035
        const val PRIOR_SD = 0.015
        const val PRIOR_NOISE = 0.05
        const val PRIOR_WEIGHT = 4.0

        /** 2B-Alert's personal predictions reached their best accuracy after about 12 tests (Reifman 2019). */
        const val MIN_TESTS = 12

        /** From here the sensitivity hardly moves, and the app asks for tests rarely. */
        const val SETTLED_TESTS = 30

        /** The model's error must be at most this times the average's, the margin Sleep factors also uses. */
        val CLEARLY: Double = sqrt(0.9)

        val POPULATION = AlertnessCalibration(0, PRIOR_SENSITIVITY, PRIOR_SD, null, PRIOR_NOISE)

        private const val ITERATIONS = 4

        /**
         * Fits the tests with alertness [x] (A - R) and log response speeds [y]; from [MIN_TESTS] tests the
         * errors come from refitting without each test in turn.
         */
        fun fit(x: DoubleArray, y: DoubleArray): AlertnessCalibration {
            require(x.size == y.size) { "One alertness per test" }
            val fitted = estimate(x, y)
            if (x.size < MIN_TESTS) return fitted
            var model = 0.0
            var average = 0.0
            for (i in x.indices) {
                val keptX = DoubleArray(x.size - 1) { j -> x[if (j < i) j else j + 1] }
                val keptY = DoubleArray(y.size - 1) { j -> y[if (j < i) j else j + 1] }
                val without = estimate(keptX, keptY)
                val predicted = checkNotNull(without.intercept) + without.sensitivity * x[i]
                model += (y[i] - predicted).let { it * it }
                average += (y[i] - keptY.average()).let { it * it }
            }
            return fitted.copy(error = sqrt(model / x.size), averageError = sqrt(average / x.size))
        }

        private fun estimate(x: DoubleArray, y: DoubleArray): AlertnessCalibration {
            val n = x.size
            if (n == 0) return POPULATION
            val meanX = x.average()
            val meanY = y.average()
            val sxx = x.sumOf { (it - meanX) * (it - meanX) }
            val sxy = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
            val priorPrecision = 1.0 / (PRIOR_SD * PRIOR_SD)
            var variance = PRIOR_NOISE * PRIOR_NOISE
            var slope = PRIOR_SENSITIVITY
            var precision = priorPrecision
            repeat(ITERATIONS) {
                precision = priorPrecision + sxx / variance
                slope = ((PRIOR_SENSITIVITY * priorPrecision + sxy / variance) / precision).coerceAtLeast(0.0)
                val intercept = meanY - slope * meanX
                val rss = x.indices.sumOf { (y[it] - intercept - slope * x[it]).let { r -> r * r } }
                variance = (PRIOR_WEIGHT * PRIOR_NOISE * PRIOR_NOISE + rss) / (PRIOR_WEIGHT + n - 1)
            }
            return AlertnessCalibration(
                tests = n,
                sensitivity = slope,
                sensitivitySd = 1.0 / sqrt(precision),
                intercept = meanY - slope * meanX,
                noise = sqrt(variance),
            )
        }
    }
}
