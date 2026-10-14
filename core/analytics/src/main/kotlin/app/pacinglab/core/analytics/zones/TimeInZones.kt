package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.ZoneSet

/** Time spent in each zone, in milliseconds, indexed like [ZoneSet.zones]. */
class ZoneDistribution(val zoneSet: ZoneSet, val millis: LongArray) {
    init {
        require(millis.size == zoneSet.size) { "Expected ${zoneSet.size} durations, got ${millis.size}" }
    }

    val totalMillis: Long get() = millis.sum()

    /** Share of the total time spent in the zone at [index], 0.0 when there is no data. */
    fun fraction(index: Int): Double = if (totalMillis == 0L) 0.0 else millis[index].toDouble() / totalMillis

    override fun equals(other: Any?): Boolean = other is ZoneDistribution && zoneSet == other.zoneSet && millis.contentEquals(other.millis)

    override fun hashCode(): Int = 31 * zoneSet.hashCode() + millis.contentHashCode()

    override fun toString(): String = "ZoneDistribution(${millis.joinToString()})"
}

object TimeInZones {
    /** Gaps longer than this are treated as missing data rather than time at the last heart rate. */
    const val DEFAULT_MAX_GAP_MILLIS = 15_000L

    /**
     * Sample-and-hold integration: each sample's heart rate applies until the next sample,
     * capped at [maxGapMillis] so that dropouts don't inflate the zone the watch last reported.
     * The final sample has no successor and contributes no time.
     */
    fun compute(series: HeartRateSeries, zones: ZoneSet, maxGapMillis: Long = DEFAULT_MAX_GAP_MILLIS): ZoneDistribution {
        require(maxGapMillis > 0) { "maxGapMillis must be positive" }
        val millis = LongArray(zones.size)
        for (i in 0 until series.size - 1) {
            val span = minOf(series.times[i + 1] - series.times[i], maxGapMillis)
            millis[zones.indexOf(series.bpm[i])] += span
        }
        return ZoneDistribution(zones, millis)
    }
}
