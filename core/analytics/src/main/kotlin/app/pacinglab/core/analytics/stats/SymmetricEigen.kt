package app.pacinglab.core.analytics.stats

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The eigendecomposition A = Q D Qᵀ of a small symmetric matrix, by cyclic Jacobi rotations. Slower than
 * a Householder reduction for large matrices, but simple, accurate to rounding and ample for the dozen
 * or so rows a regression on nightly factors needs.
 *
 * @property values the eigenvalues, the diagonal of D, in no particular order.
 * @property vectors Q by rows: `vectors[i][k]` is component i of the eigenvector of `values[k]`.
 */
class SymmetricEigen private constructor(val values: DoubleArray, val vectors: Array<DoubleArray>) {
    companion object {
        private const val MAX_SWEEPS = 100

        fun of(matrix: Array<DoubleArray>): SymmetricEigen {
            val n = matrix.size
            require(matrix.all { it.size == n }) { "The matrix must be square" }
            val a = Array(n) { matrix[it].copyOf() }
            val v = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
            val scale = (0 until n).sumOf { i -> (0 until n).sumOf { j -> a[i][j] * a[i][j] } }
            for (sweep in 0 until MAX_SWEEPS) {
                var off = 0.0
                for (p in 0 until n) for (q in p + 1 until n) off += a[p][q] * a[p][q]
                if (off <= 1e-30 * scale || off == 0.0) break
                for (p in 0 until n - 1) {
                    for (q in p + 1 until n) {
                        if (a[p][q] == 0.0) continue
                        val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                        val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                        val c = 1 / sqrt(t * t + 1)
                        val s = t * c
                        for (k in 0 until n) {
                            val kp = a[k][p]
                            val kq = a[k][q]
                            a[k][p] = c * kp - s * kq
                            a[k][q] = s * kp + c * kq
                        }
                        for (k in 0 until n) {
                            val pk = a[p][k]
                            val qk = a[q][k]
                            a[p][k] = c * pk - s * qk
                            a[q][k] = s * pk + c * qk
                        }
                        for (k in 0 until n) {
                            val kp = v[k][p]
                            val kq = v[k][q]
                            v[k][p] = c * kp - s * kq
                            v[k][q] = s * kp + c * kq
                        }
                    }
                }
            }
            return SymmetricEigen(DoubleArray(n) { a[it][it] }, v)
        }
    }
}
