package app.pacinglab.core.analytics.load

import app.pacinglab.core.analytics.zones.TimeInZones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import kotlin.math.exp

/**
 * Training load of a stretch of heart rate data, by three established models.
 *
 * @property trimp Banister's training impulse: minutes weighted by the fraction of heart rate reserve
 *   used and an exponential factor for blood lactate (Banister 1991).
 * @property stressScore [trimp] relative to one hour at threshold heart rate, times 100, so that it
 *   reads like a power-based stress score: an hour at threshold scores 100.
 * @property edwards Edwards' summated heart rate zones: minutes in the zones from 50-60 % to
 *   90-100 % of HRmax weighted 1 to 5 (Edwards 1993).
 * @property heartRateMillis time covered by heart rate samples.
 */
data class LoadScores(val trimp: Double, val stressScore: Double, val edwards: Double, val heartRateMillis: Long) {
    operator fun plus(other: LoadScores) = LoadScores(
        trimp = trimp + other.trimp,
        stressScore = stressScore + other.stressScore,
        edwards = edwards + other.edwards,
        heartRateMillis = heartRateMillis + other.heartRateMillis,
    )

    companion object {
        val ZERO = LoadScores(0.0, 0.0, 0.0, 0)
    }
}

/** The weighting of Banister's TRIMP, y = a e^(b x), fitted to blood lactate by sex. */
enum class BanisterWeighting(val a: Double, val b: Double) {
    MALE(0.64, 1.92),
    FEMALE(0.86, 1.67),
}

object HeartRateLoad {
    /**
     * Load of [series] for an athlete with [anchors]; null without a resting heart rate, which TRIMP
     * needs. Samples count until the next one, at most [maxGapMillis], as for time in zones. Heart
     * rate below resting adds nothing; above HRmax it counts as HRmax.
     */
    fun of(
        series: HeartRateSeries,
        anchors: HeartRateAnchors,
        weighting: BanisterWeighting = BanisterWeighting.MALE,
        maxGapMillis: Long = TimeInZones.DEFAULT_MAX_GAP_MILLIS,
    ): LoadScores? {
        val resting = anchors.restingHr ?: return null
        require(maxGapMillis > 0) { "maxGapMillis must be positive" }
        var trimp = 0.0
        var edwards = 0.0
        var millis = 0L
        for (i in 0 until series.size - 1) {
            val span = minOf(series.times[i + 1] - series.times[i], maxGapMillis)
            val minutes = span / MILLIS_PER_MINUTE
            val bpm = series.bpm[i]
            trimp += minutes * weight(reserveFraction(bpm, resting, anchors.maxHr), weighting)
            edwards += minutes * edwardsZone(bpm, anchors.maxHr)
            millis += span
        }
        return LoadScores(
            trimp = trimp,
            stressScore = trimp / thresholdHour(anchors, weighting) * 100,
            edwards = edwards,
            heartRateMillis = millis,
        )
    }

    /** TRIMP of one hour at threshold heart rate, the unit of [LoadScores.stressScore]. */
    fun thresholdHour(anchors: HeartRateAnchors, weighting: BanisterWeighting = BanisterWeighting.MALE): Double {
        val resting = requireNotNull(anchors.restingHr) { "TRIMP needs a resting heart rate" }
        return MINUTES_PER_HOUR * weight(reserveFraction(anchors.thresholdHr, resting, anchors.maxHr), weighting)
    }

    private fun reserveFraction(bpm: Int, resting: Int, max: Int): Double =
        ((bpm - resting).toDouble() / (max - resting)).coerceIn(0.0, 1.0)

    private fun weight(fraction: Double, weighting: BanisterWeighting): Double = fraction * weighting.a * exp(weighting.b * fraction)

    /** 0 below 50 % of HRmax, then 1 to 5 for each tenth up to and above HRmax. */
    internal fun edwardsZone(bpm: Int, max: Int): Int {
        val percent = bpm * 100 / max
        return if (percent < 50) 0 else minOf((percent - 50) / 10 + 1, 5)
    }

    private const val MILLIS_PER_MINUTE = 60_000.0
    private const val MINUTES_PER_HOUR = 60.0
}
