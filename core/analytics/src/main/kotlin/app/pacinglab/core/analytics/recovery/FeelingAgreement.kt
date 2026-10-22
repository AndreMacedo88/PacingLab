package app.pacinglab.core.analytics.recovery

import java.time.LocalDate
import kotlin.math.sqrt

/** A morning with both a sleep score for the night before and the athlete's answer to how rested they feel. */
data class RatedMorning(val date: LocalDate, val score: Int, val rested: Int)

/**
 * How well the sleep score follows how rested the athlete says they feel in the morning.
 *
 * Spearman's rank correlation suits a rating from 1 to 5: it uses only the order of the answers, not
 * equal steps between them, and one odd night cannot pull it far. Few mornings leave it uncertain, so it
 * is given only from [MIN_MORNINGS] on; even then, at ten mornings it takes about 0.65 to rule out chance
 * (two-sided p < 0.05). Strength words follow Evans (1996).
 *
 * @property mornings the mornings with both a rating and a scored night, oldest first.
 * @property rho Spearman's rho; null before [MIN_MORNINGS] mornings, or while either side has never varied.
 */
data class FeelingAgreement(val mornings: List<RatedMorning>, val rho: Double?) {
    enum class Strength {
        /** The score tends to be higher on mornings the athlete feels worse. */
        OPPOSITE,
        NONE,
        WEAK,
        MODERATE,
        STRONG,
    }

    val strength: Strength?
        get() = rho?.let {
            when {
                it <= -WEAK_FROM -> Strength.OPPOSITE
                it < WEAK_FROM -> Strength.NONE
                it < MODERATE_FROM -> Strength.WEAK
                it < STRONG_FROM -> Strength.MODERATE
                else -> Strength.STRONG
            }
        }

    companion object {
        const val MIN_MORNINGS = 10
        private const val WEAK_FROM = 0.2
        private const val MODERATE_FROM = 0.4
        private const val STRONG_FROM = 0.6

        /** Pairs each rated morning with the score of the night that ended on it. */
        fun of(scores: Map<LocalDate, SleepScore>, rested: Map<LocalDate, Int>): FeelingAgreement {
            val mornings = rested.entries.sortedBy { it.key }.mapNotNull { (date, rating) ->
                scores[date]?.let { RatedMorning(date, it.score, rating) }
            }
            val rho = if (mornings.size < MIN_MORNINGS) {
                null
            } else {
                Spearman.rho(mornings.map { it.score.toDouble() }, mornings.map { it.rested.toDouble() })
            }
            return FeelingAgreement(mornings, rho)
        }
    }
}

/** Spearman's rank correlation, with tied values sharing the mean of their ranks. */
object Spearman {
    /** Rho of the paired values; null for fewer than three pairs or when either side is constant. */
    fun rho(x: List<Double>, y: List<Double>): Double? {
        require(x.size == y.size) { "Unpaired values: ${x.size} and ${y.size}" }
        if (x.size < 3) return null
        return pearson(ranks(x), ranks(y))
    }

    /** Ranks from 1, ties given the mean of the ranks they span. */
    internal fun ranks(values: List<Double>): DoubleArray {
        val order = values.indices.sortedBy { values[it] }
        val ranks = DoubleArray(values.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && values[order[j + 1]] == values[order[i]]) j++
            val rank = (i + j) / 2.0 + 1
            for (k in i..j) ranks[order[k]] = rank
            i = j + 1
        }
        return ranks
    }

    private fun pearson(x: DoubleArray, y: DoubleArray): Double? {
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in x.indices) {
            sxy += (x[i] - mx) * (y[i] - my)
            sxx += (x[i] - mx) * (x[i] - mx)
            syy += (y[i] - my) * (y[i] - my)
        }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / sqrt(sxx * syy)
    }
}
