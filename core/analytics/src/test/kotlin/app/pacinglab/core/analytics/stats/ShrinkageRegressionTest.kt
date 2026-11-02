package app.pacinglab.core.analytics.stats

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

class ShrinkageRegressionTest {
    @Test
    fun `Student t matches tabulated values`() {
        assertThat(StudentT.cdf(0.0, 5.0)).isCloseTo(0.5, 1e-12)
        // Cauchy: arctan(1)/π + 1/2.
        assertThat(StudentT.cdf(1.0, 1.0)).isCloseTo(0.75, 1e-12)
        assertThat(StudentT.cdf(1.8125, 10.0)).isCloseTo(0.95, 1e-4)
        assertThat(StudentT.cdf(-1.311, 29.0)).isCloseTo(0.10, 1e-4)
        assertThat(StudentT.cdf(2.756, 29.0)).isCloseTo(0.995, 1e-4)
        assertThat(lnGamma(5.0)).isCloseTo(ln(24.0), 1e-12)
        assertThat(lnGamma(0.5)).isCloseTo(0.5 * ln(PI), 1e-12)
    }

    @Test
    fun `the eigendecomposition rebuilds the matrix`() {
        val a = arrayOf(
            doubleArrayOf(4.0, 1.0, -2.0, 0.5),
            doubleArrayOf(1.0, 3.0, 0.0, 1.5),
            doubleArrayOf(-2.0, 0.0, 5.0, -1.0),
            doubleArrayOf(0.5, 1.5, -1.0, 2.0),
        )
        val eigen = SymmetricEigen.of(a)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                val rebuilt = (0 until 4).sumOf { k -> eigen.vectors[i][k] * eigen.values[k] * eigen.vectors[j][k] }
                assertThat(rebuilt).isCloseTo(a[i][j], 1e-10)
            }
        }
    }

    @Test
    fun `with one tau and plain errors it is ridge regression, and its left-out errors match refitting`() {
        val random = Random(7)
        val x = List(25) { doubleArrayOf(random.nextGaussian(), random.nextGaussian(), if (random.nextBoolean()) 1.0 else 0.0) }
        val y = DoubleArray(25) { 3 + 2 * x[it][0] - x[it][1] + 1.5 * x[it][2] + random.nextGaussian() }
        val tau = doubleArrayOf(0.7)
        val gaussian = 1e12

        val fit = ShrinkageRegression.fit(x, y, nu = gaussian, tau = tau)!!

        val ridge = ridge(x, y, 1 / (0.7 * 0.7))
        for (j in 0 until 3) assertThat(fit.mean(j)).isCloseTo(ridge[j], 1e-8)
        val refitted = (0 until 25).sumOf { i ->
            val others = (0 until 25).filter { it != i }
            val leftOut = ShrinkageRegression.fit(others.map { x[it] }, DoubleArray(24) { y[others[it]] }, nu = gaussian, tau = tau)!!
            val prediction = leftOut.intercept + (0 until 3).sumOf { leftOut.mean(it) * (x[i][it] - leftOut.centers[it]) }
            (y[i] - prediction) * (y[i] - prediction)
        } / 25
        assertThat(fit.looError).isCloseTo(refitted, 1e-8)
    }

    @Test
    fun `with plenty of data the effects come out where they were planted`() {
        val random = Random(11)
        val x = List(400) { doubleArrayOf(random.nextGaussian(), if (random.nextBoolean()) 1.0 else 0.0, random.nextGaussian()) }
        val y = DoubleArray(400) { 70 + 4 * x[it][0] - 6 * x[it][1] + 10 * random.nextGaussian() }

        val fit = ShrinkageRegression.fit(x, y)!!

        assertThat(fit.mean(0)).isBetween(2.8, 5.2)
        assertThat(fit.mean(1)).isBetween(-8.0, -4.0)
        assertThat(abs(fit.mean(2))).isLessThan(1.5)
        assertThat(fit.quantile(0, 0.1)).isLessThan(fit.mean(0))
        assertThat(fit.quantile(0, 0.9)).isGreaterThan(fit.mean(0))
        assertThat(fit.skill).isGreaterThan(0.05)
    }

    @Test
    fun `on pure noise it rarely calls an effect clear`() {
        val random = Random(3)
        var clear = 0
        var factors = 0
        for (size in listOf(30, 60, 120)) {
            repeat(150) {
                val x = List(size) {
                    doubleArrayOf(
                        random.nextGaussian(),
                        random.nextGaussian(),
                        if (random.nextDouble() < 0.3) 1.0 else 0.0,
                        random.nextGaussian(),
                        if (random.nextBoolean()) 1.0 else 0.0,
                        random.nextGaussian(),
                    )
                }
                val y = DoubleArray(size) { 75 + 10 * random.nextGaussian() }
                val fit = ShrinkageRegression.fit(x, y)!!
                for (j in 0 until 6) {
                    factors++
                    val beyond = maxOf(1 - fit.cdf(j, MINIMUM), fit.cdf(j, -MINIMUM))
                    if (beyond >= CLEAR && fit.skill > 0) clear++
                }
            }
        }
        assertThat(clear.toDouble() / factors).isLessThan(0.01)
    }

    @Test
    fun `a moderate effect is found once there are enough nights`() {
        val random = Random(5)
        var found = 0
        repeat(100) {
            // Predictors in units of their interquartile range, as the sleep factors are.
            val x = List(120) { DoubleArray(4) { random.nextGaussian() / IQR_PER_SD } }
            // Five points per typical change, half the night-to-night spread.
            val y = DoubleArray(120) { 75 + 5 * x[it][0] + 10 * random.nextGaussian() }
            val fit = ShrinkageRegression.fit(x, y)!!
            if (1 - fit.cdf(0, MINIMUM) >= CLEAR && fit.skill > 0) found++
        }
        // About three in four in the power study behind the info text.
        assertThat(found).isGreaterThan(60)
    }

    @Test
    fun `a factor that only travels with the real cause is not blamed once the cause is in the model`() {
        val random = Random(9)
        val cause = DoubleArray(300) { random.nextGaussian() }
        val companion = DoubleArray(300) { 0.8 * cause[it] + 0.6 * random.nextGaussian() }
        val y = DoubleArray(300) { 70 + 5 * cause[it] + 8 * random.nextGaussian() }

        val alone = ShrinkageRegression.fit(List(300) { doubleArrayOf(companion[it]) }, y)!!
        val both = ShrinkageRegression.fit(List(300) { doubleArrayOf(companion[it], cause[it]) }, y)!!

        assertThat(alone.quantile(0, 0.1)).isGreaterThan(2.0)
        assertThat(both.quantile(0, 0.1)).isLessThan(MINIMUM)
        assertThat(both.mean(0)).isLessThan(1.5)
        assertThat(both.mean(1)).isBetween(3.5, 6.5)
    }

    @Test
    fun `a few wrecked nights barely move the estimate and count less`() {
        val random = Random(13)
        val x = List(60) { doubleArrayOf(random.nextGaussian()) }
        val clean = DoubleArray(60) { 75 + 3 * x[it][0] + 5 * random.nextGaussian() }
        val wrecked = clean.copyOf().also {
            it[10] -= 50
            it[20] -= 45
        }

        val reference = ShrinkageRegression.fit(x, clean)!!
        val robust = ShrinkageRegression.fit(x, wrecked)!!

        assertThat(abs(robust.mean(0) - reference.mean(0))).isLessThan(0.6)
        assertThat(robust.weights[10]).isLessThan(0.1)
        assertThat(robust.weights[30]).isGreaterThan(0.5)
    }

    @Test
    fun `runs of similar nights widen the spread`() {
        val random = Random(17)
        var e = 0.0
        var z = 0.0
        val x = mutableListOf<DoubleArray>()
        val y = DoubleArray(200)
        for (i in 0 until 200) {
            e = 0.7 * e + sqrt(1 - 0.49) * random.nextGaussian()
            z = 0.7 * z + sqrt(1 - 0.49) * random.nextGaussian()
            x += doubleArrayOf(z)
            y[i] = 70 + 8 * e
        }
        val follows = BooleanArray(200) { it > 0 }

        val fit = ShrinkageRegression.fit(x, y, follows)!!

        // √((1 + 0.49) / (1 − 0.49)) = 1.71 for both at 0.7.
        assertThat(fit.inflation[0]).isBetween(1.45, 2.0)
        assertThat(ShrinkageRegression.fit(x, y)!!.inflation[0]).isCloseTo(1.0, 1e-12)
    }

    @Test
    fun `nothing to explain gives no fit`() {
        assertThat(ShrinkageRegression.fit(List(5) { doubleArrayOf(it.toDouble()) }, DoubleArray(5) { 70.0 })).isNull()
        assertThat(ShrinkageRegression.fit(List(3) { DoubleArray(0) }, doubleArrayOf(1.0, 2.0, 4.0))).isNotNull()
    }

    /** Ridge regression with an unpenalised intercept, solved directly by Gaussian elimination. */
    private fun ridge(x: List<DoubleArray>, y: DoubleArray, penalty: Double): DoubleArray {
        val p = x.first().size
        val xBar = DoubleArray(p) { j -> x.sumOf { it[j] } / x.size }
        val yBar = y.average()
        val a = Array(p) { j -> DoubleArray(p + 1) }
        for (j in 0 until p) {
            for (l in 0 until p) a[j][l] = x.sumOf { (it[j] - xBar[j]) * (it[l] - xBar[l]) } + if (j == l) penalty else 0.0
            a[j][p] = x.indices.sumOf { (x[it][j] - xBar[j]) * (y[it] - yBar) }
        }
        for (c in 0 until p) {
            for (r in c + 1 until p) {
                val f = a[r][c] / a[c][c]
                for (k in c..p) a[r][k] -= f * a[c][k]
            }
        }
        val beta = DoubleArray(p)
        for (r in p - 1 downTo 0) beta[r] = (a[r][p] - (r + 1 until p).sumOf { a[r][it] * beta[it] }) / a[r][r]
        return beta
    }

    private companion object {
        const val MINIMUM = 1.0
        const val IQR_PER_SD = 1.349
        const val CLEAR = 0.9
    }
}
