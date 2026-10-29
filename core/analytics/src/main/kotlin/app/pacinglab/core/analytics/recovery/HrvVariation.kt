package app.pacinglab.core.analytics.recovery

import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * How much nightly HRV varied over the last week: the coefficient of variation of ln(RMSSD) over seven
 * days, in per cent (Plews 2012, 2013; used by HRV4Training next to the 7-day mean). A mean that holds
 * while the variation rises points to a disturbed balance (hard training, illness, travel). In elite
 * triathletes a falling mean with a shrinking variation preceded non-functional overreaching (Plews
 * 2012), so the direction alone is not a verdict; the athlete's own usual range is the reference.
 *
 * @property percent the week's coefficient of variation.
 * @property range the 10th to 90th percentile of the weekly values of the previous [BASELINE_DAYS] days;
 *   null while there are fewer than [MIN_BASELINE_WEEKS] of them.
 */
data class HrvVariation(val date: LocalDate, val percent: Double, val nights: Int, val range: ClosedFloatingPointRange<Double>?) {
    companion object {
        const val WEEK_DAYS = 7L

        /** Nights of HRV a week needs; HRV4Training asks for at least five in seven. */
        const val MIN_NIGHTS = 5
        const val BASELINE_DAYS = 60L
        const val MIN_BASELINE_WEEKS = 14

        /** The variation of the week up to [date]; null with fewer than [MIN_NIGHTS] nights of HRV in it. */
        fun of(nights: List<Night>, date: LocalDate): HrvVariation? {
            val percent = weekPercent(nights, date) ?: return null
            val baseline = (1..BASELINE_DAYS).mapNotNull { weekPercent(nights, date.minusDays(it)) }.sorted()
            val range = if (baseline.size >= MIN_BASELINE_WEEKS) {
                NightlyRange.quantile(baseline, LOW)..NightlyRange.quantile(baseline, HIGH)
            } else {
                null
            }
            val count = nights.count { it.hrvMs != null && it.date <= date && it.date > date.minusDays(WEEK_DAYS) }
            return HrvVariation(date, percent, count, range)
        }

        private fun weekPercent(nights: List<Night>, date: LocalDate): Double? {
            val logs = nights.filter {
                it.date <= date && it.date > date.minusDays(WEEK_DAYS)
            }.mapNotNull { it.hrvMs?.takeIf { v -> v > 0 } }.map(::ln)
            if (logs.size < MIN_NIGHTS) return null
            val mean = logs.average()
            val sd = sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1))
            return 100 * sd / mean
        }

        private const val LOW = 0.1
        private const val HIGH = 0.9
    }
}
