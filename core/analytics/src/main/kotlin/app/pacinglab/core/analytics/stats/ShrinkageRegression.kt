package app.pacinglab.core.analytics.stats

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Bayesian linear regression for few observations and several predictors at once, robust to the odd
 * observation and sceptical of every effect until the data argue for it.
 *
 * For observations i = 1..n with predictors x_i:
 *
 * - y_i = α + x_iᵀβ + ε_i, with ε_i | ω_i ~ N(0, σ²/ω_i) and ω_i ~ Gamma(ν/2, rate ν/2), which makes the
 *   errors Student-t with ν degrees of freedom: a few observations far off the line count less instead
 *   of dragging it (Lange, Little and Taylor 1989).
 * - p(α) ∝ 1, β | σ², τ ~ N(0, τ²σ²I), p(σ²) ∝ 1/σ²: every effect starts at zero, with a spread τ shared by
 *   all of them, so that one clear effect does not license the rest (Gelman, Hill and Yajima 2012).
 * - τ is not fixed: it takes [GRID] values between 0.02 and 2 with half-Cauchy(0, [TAU_SCALE]) prior
 *   weights (Gelman 2006), and each value is weighted by how well it explains the data (its marginal
 *   likelihood), which is the posterior of τ on that grid.
 *
 * The weights ω_i are found by expectation-maximisation and then held fixed. Given them the posterior is
 * exact and in closed form: for each τ a normal-inverse-gamma posterior, whose marginal for each β_j is a
 * scaled Student-t with n − 1 degrees of freedom, and over τ a mixture of those. One eigendecomposition of
 * XᵀWX serves every τ on the grid.
 *
 * Observations close in time are rarely independent. When both the residuals and a predictor are
 * correlated from one observation to the next, the spread of its coefficient is widened by
 * √((1 + ρ_e ρ_x) / (1 − ρ_e ρ_x)), the variance inflation of a regression with first-order autocorrelated
 * errors and predictor (Bartlett 1935).
 *
 * Predictions for observations left out one at a time come from the hat matrix (exact for each τ with the
 * weights fixed), and are compared with those of the weighted mean alone by their mean squared error,
 * each observation counted by its weight as in the fit: a model that cannot beat the mean on observations
 * it has not seen has found nothing to rely on.
 *
 * @property observations n.
 * @property intercept the weighted mean of y, where the fitted line passes through the weighted means of x.
 * @property centers the weighted mean of each predictor.
 * @property weights ω_i of each observation; 1 for an ordinary one, smaller the further it lies off.
 * @property fitted the posterior mean of α + x_iᵀβ for each observation.
 * @property looFitted the prediction of each observation from all the others, as [looError] measures it.
 * @property tauWeights the posterior weight of each grid value of τ.
 * @property inflation the autocorrelation widening of each coefficient's spread, at least 1.
 * @property looError weighted mean squared error predicting each observation from the others.
 * @property baselineLooError the same for the weighted mean of the others.
 */
class ShrinkageRegression private constructor(
    val observations: Int,
    val intercept: Double,
    val centers: DoubleArray,
    val weights: DoubleArray,
    val fitted: DoubleArray,
    val looFitted: DoubleArray,
    val tauWeights: DoubleArray,
    val inflation: DoubleArray,
    val looError: Double,
    val baselineLooError: Double,
    private val means: Array<DoubleArray>,
    private val scales: Array<DoubleArray>,
) {
    /** Degrees of freedom of each coefficient's posterior: n − 1, one taken by the intercept. */
    val dof: Double get() = observations - 1.0

    val coefficients: Int get() = centers.size

    /** How much better than the mean the model predicts left-out observations: 1 − [looError] / [baselineLooError]. */
    val skill: Double get() = if (baselineLooError > 0) 1 - looError / baselineLooError else 0.0

    /** The posterior mean of coefficient [j]. */
    fun mean(j: Int): Double = means.indices.sumOf { k -> tauWeights[k] * means[k][j] }

    /** The posterior mean of α + xᵀβ for new predictors [x], on the scale of those the model was fitted on. */
    fun predict(x: DoubleArray): Double {
        require(x.size == coefficients) { "Expected $coefficients predictors, got ${x.size}" }
        return intercept + x.indices.sumOf { j -> (x[j] - centers[j]) * mean(j) }
    }

    /** P(β_j <= [value]). */
    fun cdf(j: Int, value: Double): Double = means.indices.sumOf { k ->
        tauWeights[k] * StudentT.cdf((value - means[k][j]) / scales[k][j], dof)
    }

    /** The [q] quantile of coefficient [j]'s posterior, found by bisection. */
    fun quantile(j: Int, q: Double): Double {
        require(q > 0 && q < 1) { "A quantile lies strictly between 0 and 1, got $q" }
        var low = means.indices.minOf { means[it][j] - SEARCH_WIDTH * scales[it][j] }
        var high = means.indices.maxOf { means[it][j] + SEARCH_WIDTH * scales[it][j] }
        repeat(BISECTIONS) {
            val middle = (low + high) / 2
            if (cdf(j, middle) < q) low = middle else high = middle
        }
        return (low + high) / 2
    }

    companion object {
        /** Degrees of freedom of the error distribution; four is the usual robust choice. */
        const val DEFAULT_NU = 4.0

        /** Scale of the half-Cauchy prior on τ, in residual standard deviations per unit of a predictor. */
        const val TAU_SCALE = 0.5

        const val GRID = 41
        private const val TAU_MIN = 0.02
        private const val TAU_MAX = 2.0
        private const val MAX_EM = 200
        private const val EM_TOLERANCE = 1e-8
        private const val MIN_PAIRS = 10
        private const val MAX_RHO_PRODUCT = 0.8
        private const val SEARCH_WIDTH = 60.0
        private const val BISECTIONS = 80

        /** The grid of τ values, evenly spaced on a log scale. */
        val TAU: DoubleArray = DoubleArray(GRID) { k -> exp(ln(TAU_MIN) + k * (ln(TAU_MAX) - ln(TAU_MIN)) / (GRID - 1)) }

        /**
         * Fits y on [x] (one row of predictors per observation). [follows] marks observations that come
         * right after the one before them in time, for the autocorrelation check; without it none do.
         * [tau] replaces the grid of τ values (a single value fixes it). Null for fewer than three
         * observations or when y never varies.
         */
        fun fit(
            x: List<DoubleArray>,
            y: DoubleArray,
            follows: BooleanArray = BooleanArray(y.size),
            nu: Double = DEFAULT_NU,
            tau: DoubleArray = TAU,
        ): ShrinkageRegression? {
            val n = y.size
            require(x.size == n && follows.size == n) { "Every observation needs predictors and an order flag" }
            val p = x.firstOrNull()?.size ?: 0
            require(x.all { it.size == p }) { "Every observation needs the same predictors" }
            if (n < 3 || y.all { it == y[0] }) return null

            var omega = DoubleArray(n) { 1.0 }
            var state = Conditional(x, y, omega, tau)
            for (iteration in 0 until MAX_EM) {
                val sigma2 = state.sigma2
                if (sigma2 <= 0) break
                val next = DoubleArray(n) { i ->
                    val r = y[i] - state.fitted[i]
                    (nu + 1) / (nu + r * r / sigma2)
                }
                val change = next.indices.maxOf { abs(next[it] - omega[it]) }
                omega = next
                state = Conditional(x, y, omega, tau)
                if (change < EM_TOLERANCE) break
            }

            val residuals = DoubleArray(n) { (y[it] - state.fitted[it]) * sqrt(omega[it]) }
            val rhoResiduals = lagOne(residuals, follows)
            val inflation = DoubleArray(p) { j ->
                val product = min(rhoResiduals * lagOne(DoubleArray(n) { state.xc[it][j] }, follows), MAX_RHO_PRODUCT)
                if (product > 0) sqrt((1 + product) / (1 - product)) else 1.0
            }
            return ShrinkageRegression(
                observations = n,
                intercept = state.yBar,
                centers = state.xBar,
                weights = omega,
                fitted = state.fitted,
                looFitted = state.looFitted,
                tauWeights = state.tauWeights,
                inflation = inflation,
                looError = state.looError,
                baselineLooError = state.baselineLooError,
                means = state.means,
                scales = Array(tau.size) { k -> DoubleArray(p) { j -> state.scales[k][j] * inflation[j] } },
            )
        }

        /** First-order autocorrelation over the pairs of consecutive observations; zero with too few pairs. */
        internal fun lagOne(values: DoubleArray, follows: BooleanArray): Double {
            val mean = values.average()
            val pairs = (1 until values.size).filter { follows[it] }
            if (pairs.size < MIN_PAIRS) return 0.0
            val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
            if (variance == 0.0) return 0.0
            val covariance = pairs.sumOf { (values[it] - mean) * (values[it - 1] - mean) } / pairs.size
            return (covariance / variance).coerceIn(-1.0, 1.0)
        }
    }

    /** The exact posterior for fixed observation weights [omega]. */
    private class Conditional(x: List<DoubleArray>, y: DoubleArray, omega: DoubleArray, private val grid: DoubleArray) {
        val n = y.size
        val p = x.first().size
        private val sumW = omega.sum()
        val xBar = DoubleArray(p) { j -> x.indices.sumOf { omega[it] * x[it][j] } / sumW }
        val yBar = y.indices.sumOf { omega[it] * y[it] } / sumW
        val xc = Array(n) { i -> DoubleArray(p) { j -> x[i][j] - xBar[j] } }
        private val yc = DoubleArray(n) { y[it] - yBar }

        val means = Array(grid.size) { DoubleArray(p) }
        val scales = Array(grid.size) { DoubleArray(p) }
        val tauWeights = DoubleArray(grid.size)
        val fitted = DoubleArray(n)
        val looFitted = DoubleArray(n)
        var sigma2 = 0.0
        var looError = 0.0
        var baselineLooError = 0.0

        init {
            val gram = Array(p) { j -> DoubleArray(p) { l -> (0 until n).sumOf { omega[it] * xc[it][j] * xc[it][l] } } }
            val cross = DoubleArray(p) { j -> (0 until n).sumOf { omega[it] * xc[it][j] * yc[it] } }
            val total = (0 until n).sumOf { omega[it] * yc[it] * yc[it] }
            val eigen = SymmetricEigen.of(gram)
            val d = DoubleArray(p) { max(eigen.values[it], 0.0) }
            val q = eigen.vectors
            val u = DoubleArray(p) { l -> (0 until p).sumOf { j -> q[j][l] * cross[j] } }
            // Each observation's predictors in the eigenbasis, for the leverages.
            val rotated = Array(n) { i -> DoubleArray(p) { l -> (0 until p).sumOf { j -> q[j][l] * xc[i][j] } } }
            val shape = (n - 1) / 2.0
            val logWeights = DoubleArray(grid.size)
            val rates = DoubleArray(grid.size)
            val gains = Array(grid.size) { DoubleArray(p) }
            for (k in grid.indices) {
                val tau = grid[k]
                val precision = 1 / (tau * tau)
                val g = DoubleArray(p) { 1 / (d[it] + precision) }
                gains[k] = g
                for (j in 0 until p) means[k][j] = (0 until p).sumOf { l -> q[j][l] * g[l] * u[l] }
                val explained = (0 until p).sumOf { g[it] * u[it] * u[it] }
                val rate = max((total - explained) / 2, total * 1e-12 + Double.MIN_VALUE)
                rates[k] = rate
                for (j in 0 until p) scales[k][j] = sqrt(rate / shape * (0 until p).sumOf { l -> q[j][l] * q[j][l] * g[l] })
                val prior = ln(tau) - ln(1 + (tau / TAU_SCALE) * (tau / TAU_SCALE))
                logWeights[k] = prior - p * ln(tau) - 0.5 * (0 until p).sumOf { ln(d[it] + precision) } - shape * ln(rate)
            }
            val top = logWeights.max()
            for (k in grid.indices) tauWeights[k] = exp(logWeights[k] - top)
            val sum = tauWeights.sum()
            for (k in grid.indices) tauWeights[k] /= sum
            sigma2 = grid.indices.sumOf { tauWeights[it] * rates[it] / shape }

            var modelError = 0.0
            var meanError = 0.0
            for (i in 0 until n) {
                val own = omega[i] / sumW
                var prediction = 0.0
                var leftOut = 0.0
                for (k in grid.indices) {
                    val fit = (0 until p).sumOf { xc[i][it] * means[k][it] }
                    prediction += tauWeights[k] * fit
                    val leverage = own + omega[i] * (0 until p).sumOf { gains[k][it] * rotated[i][it] * rotated[i][it] }
                    leftOut += tauWeights[k] * (yc[i] - fit) / max(1 - leverage, 1e-9)
                }
                fitted[i] = yBar + prediction
                looFitted[i] = y[i] - leftOut
                val alone = yc[i] / max(1 - own, 1e-9)
                modelError += omega[i] * leftOut * leftOut
                meanError += omega[i] * alone * alone
            }
            looError = modelError / sumW
            baselineLooError = meanError / sumW
        }
    }
}
