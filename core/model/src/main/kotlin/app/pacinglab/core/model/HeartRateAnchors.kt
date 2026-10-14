package app.pacinglab.core.model

/**
 * The heart rate values that intensity zones and load models are anchored on.
 *
 * @property maxHr maximal heart rate, bpm.
 * @property thresholdHr lactate threshold heart rate (LTHR), bpm.
 * @property restingHr resting heart rate, bpm.
 */
data class HeartRateAnchors(val maxHr: Int, val thresholdHr: Int, val restingHr: Int) {
    init {
        require(maxHr in MAX_HR_RANGE) { "maxHr $maxHr outside $MAX_HR_RANGE" }
        require(restingHr in RESTING_HR_RANGE) { "restingHr $restingHr outside $RESTING_HR_RANGE" }
        require(thresholdHr in (restingHr + 1) until maxHr) {
            "thresholdHr $thresholdHr must lie between restingHr $restingHr and maxHr $maxHr"
        }
    }

    /** Heart rate reserve (Karvonen): the span between resting and maximal heart rate. */
    val reserve: Int get() = maxHr - restingHr

    companion object {
        val MAX_HR_RANGE = 120..240
        val RESTING_HR_RANGE = 25..120
    }
}
