package app.pacinglab.core.analytics.stats

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/** Student's t distribution, centred at zero with unit scale. */
object StudentT {
    /** P(T <= [t]) for [dof] degrees of freedom, through the regularized incomplete beta function. */
    fun cdf(t: Double, dof: Double): Double {
        require(dof > 0) { "Degrees of freedom must be positive, got $dof" }
        if (t.isNaN()) return Double.NaN
        if (t == Double.POSITIVE_INFINITY) return 1.0
        if (t == Double.NEGATIVE_INFINITY) return 0.0
        val tail = 0.5 * regularizedBeta(dof / (dof + t * t), dof / 2, 0.5)
        return if (t > 0) 1 - tail else tail
    }
}

/** ln Γ([x]) for x > 0, by the Lanczos approximation (g = 7, nine terms; relative error near 1e-15). */
fun lnGamma(x: Double): Double {
    require(x > 0) { "lnGamma needs a positive argument, got $x" }
    if (x < 0.5) return ln(PI / abs(sin(PI * x))) - lnGamma(1 - x)
    val z = x - 1
    var sum = LANCZOS[0]
    for (i in 1 until LANCZOS.size) sum += LANCZOS[i] / (z + i)
    val t = z + LANCZOS_G + 0.5
    return 0.5 * ln(2 * PI) + (z + 0.5) * ln(t) - t + ln(sum)
}

/** The regularized incomplete beta function I_x(a, b), by Lentz's continued fraction (Numerical Recipes 6.4). */
fun regularizedBeta(x: Double, a: Double, b: Double): Double {
    require(a > 0 && b > 0) { "Beta parameters must be positive, got $a and $b" }
    if (x <= 0) return 0.0
    if (x >= 1) return 1.0
    val front = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1 - x))
    // The fraction converges quickly on this side of the mean; the other side follows by symmetry.
    return if (x < (a + 1) / (a + b + 2)) {
        front * betaFraction(x, a, b) / a
    } else {
        1 - front * betaFraction(1 - x, b, a) / b
    }
}

private fun betaFraction(x: Double, a: Double, b: Double): Double {
    fun guard(v: Double) = if (abs(v) < TINY) TINY else v
    var c = 1.0
    var d = 1 / guard(1 - (a + b) * x / (a + 1))
    var h = d
    for (m in 1..MAX_TERMS) {
        val even = m * (b - m) * x / ((a - 1 + 2 * m) * (a + 2 * m))
        d = 1 / guard(1 + even * d)
        c = guard(1 + even / c)
        h *= d * c
        val odd = -(a + m) * (a + b + m) * x / ((a + 2 * m) * (a + 1 + 2 * m))
        d = 1 / guard(1 + odd * d)
        c = guard(1 + odd / c)
        val step = d * c
        h *= step
        if (abs(step - 1) < EPSILON) break
    }
    return h
}

private const val LANCZOS_G = 7.0
private val LANCZOS = doubleArrayOf(
    0.99999999999980993,
    676.5203681218851,
    -1259.1392167224028,
    771.32342877765313,
    -176.61502916214059,
    12.507343278686905,
    -0.13857109526572012,
    9.9843695780195716e-6,
    1.5056327351493116e-7,
)
private const val MAX_TERMS = 300
private const val EPSILON = 1e-15
private const val TINY = 1e-300
