package app.pacinglab.core.model

/**
 * The heart rate values that intensity zones and load models are anchored on.
 *
 * @property maxHr maximal heart rate, bpm.
 * @property thresholdHr lactate threshold heart rate (LTHR), bpm.
 * @property restingHr resting heart rate, bpm; only models based on heart rate reserve need it.
 */
data class HeartRateAnchors(val maxHr: Int, val thresholdHr: Int, val restingHr: Int? = null) {
    init {
        require(maxHr in MAX_HR_RANGE) { "maxHr $maxHr outside $MAX_HR_RANGE" }
        restingHr?.let { require(it in RESTING_HR_RANGE) { "restingHr $it outside $RESTING_HR_RANGE" } }
        val lowest = maxOf(MIN_THRESHOLD_HR, (restingHr ?: 0) + 1)
        require(thresholdHr in lowest until maxHr) {
            "thresholdHr $thresholdHr must lie between $lowest and maxHr $maxHr"
        }
    }

    /** Heart rate reserve (Karvonen): the span between resting and maximal heart rate. */
    val reserve: Int? get() = restingHr?.let { maxHr - it }

    companion object {
        val MAX_HR_RANGE = 120..240
        val RESTING_HR_RANGE = 25..120
        const val MIN_THRESHOLD_HR = 80
    }
}
