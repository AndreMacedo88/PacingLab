package app.pacinglab.core.model

/**
 * Zone boundaries the athlete set in place of the ones worked out from threshold heart rate, for
 * example from a lab test. Each list holds the lowest heart rate of every zone above the first.
 *
 * @property five where zones 2 to 5 of the five-zone model start; null to work them out.
 * @property polarized where the second and third polarized zones start (VT1 and VT2); null to work them out.
 */
data class ZoneBoundaries(val five: List<Int>? = null, val polarized: List<Int>? = null) {
    init {
        five?.let { require(it.size == 4 && it.isAscending()) { "Five zones need four rising boundaries: $it" } }
        polarized?.let { require(it.size == 2 && it.isAscending()) { "Polarized zones need two rising boundaries: $it" } }
    }

    val isEmpty: Boolean get() = five == null && polarized == null

    private fun List<Int>.isAscending() = zipWithNext().all { (a, b) -> b > a } && first() > 0

    companion object {
        val BPM_RANGE = 60..230
    }
}
