package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import kotlin.math.roundToInt

object ThresholdEstimates {
    /**
     * Fraction of HRmax used as a starting LTHR when no threshold test exists.
     * Trained runners typically sit between 0.85 and 0.92; the app flags the value as an
     * estimate until it is measured or detected from training data.
     */
    const val LTHR_FRACTION_OF_MAX = 0.90

    fun thresholdFromMax(maxHr: Int): Int = (maxHr * LTHR_FRACTION_OF_MAX).roundToInt()

    /** Anchors for [entry], with LTHR estimated from HRmax when the athlete has not set it. */
    fun anchors(entry: PhysiologyEntry): HeartRateAnchors =
        HeartRateAnchors(maxHr = entry.maxHr, thresholdHr = entry.thresholdHr ?: thresholdFromMax(entry.maxHr), restingHr = entry.restingHr)
}
