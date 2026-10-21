package app.pacinglab.core.model

import java.time.LocalDate

/**
 * Heart rate values the athlete set, valid from [effectiveFrom] until the next entry, so that older
 * sessions keep being analysed with the values that applied when they were run. Every value may be
 * left empty, in which case PacingLab uses its own: maximal heart rate estimated from workouts,
 * threshold as a share of the maximum, resting heart rate measured during sleep.
 */
data class PhysiologyEntry(val effectiveFrom: LocalDate, val maxHr: Int? = null, val thresholdHr: Int? = null, val restingHr: Int? = null) {
    init {
        maxHr?.let { require(it in HeartRateAnchors.MAX_HR_RANGE) { "maxHr $it outside ${HeartRateAnchors.MAX_HR_RANGE}" } }
        restingHr?.let {
            require(it in HeartRateAnchors.RESTING_HR_RANGE && it < (maxHr ?: Int.MAX_VALUE)) { "restingHr $it is implausible" }
        }
        thresholdHr?.let {
            require(it >= HeartRateAnchors.MIN_THRESHOLD_HR && it < (maxHr ?: Int.MAX_VALUE) && it > (restingHr ?: 0)) {
                "thresholdHr $it is implausible"
            }
        }
    }
}

/** Every [PhysiologyEntry], oldest first, at most one per day. */
class PhysiologyHistory(entries: List<PhysiologyEntry>) {
    val entries: List<PhysiologyEntry> = entries.sortedBy { it.effectiveFrom }

    init {
        require(this.entries.zipWithNext().none { (a, b) -> a.effectiveFrom == b.effectiveFrom }) { "One entry per day at most" }
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    /** The most recent entry, which applies from its date onwards. */
    val current: PhysiologyEntry? get() = entries.lastOrNull()

    /**
     * The entry valid on [date]. Dates before the first entry use the first one: the history usually
     * starts after the training it should describe, and these values change slowly.
     */
    fun at(date: LocalDate): PhysiologyEntry? = entries.lastOrNull { !it.effectiveFrom.isAfter(date) } ?: entries.firstOrNull()

    override fun equals(other: Any?): Boolean = other is PhysiologyHistory && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "PhysiologyHistory($entries)"

    companion object {
        val EMPTY = PhysiologyHistory(emptyList())
    }
}
