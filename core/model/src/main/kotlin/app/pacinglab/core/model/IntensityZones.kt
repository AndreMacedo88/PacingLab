package app.pacinglab.core.model

/**
 * One intensity zone. The zone covers heart rates from [lowerBpm] up to, but not including,
 * the next zone's lower bound. The last zone is open-ended.
 */
data class IntensityZone(val number: Int, val label: String, val lowerBpm: Int)

/** An ordered set of contiguous heart rate zones, numbered from 1. */
data class ZoneSet(val model: ZoneModel, val zones: List<IntensityZone>) {
    init {
        require(zones.isNotEmpty()) { "A zone set needs at least one zone" }
        zones.forEachIndexed { index, zone ->
            require(zone.number == index + 1) { "Zones must be numbered 1..n in order" }
        }
        zones.zipWithNext { a, b ->
            require(b.lowerBpm > a.lowerBpm) { "Zone ${b.number} must start above zone ${a.number}" }
        }
    }

    val size: Int get() = zones.size

    /** Index (0-based) of the zone containing [bpm]. Heart rates below zone 1 count as zone 1. */
    fun indexOf(bpm: Int): Int {
        for (i in zones.lastIndex downTo 1) {
            if (bpm >= zones[i].lowerBpm) return i
        }
        return 0
    }

    /** Exclusive upper bound of the zone at [index], or null for the open-ended top zone. */
    fun upperBpm(index: Int): Int? = zones.getOrNull(index + 1)?.lowerBpm
}

/** The supported ways of deriving zones from [HeartRateAnchors]. */
enum class ZoneModel {
    /** Five zones as fractions of LTHR (Friel running zones with 5a folded into zone 4). */
    LTHR_FIVE,

    /** Five zones as fractions of heart rate reserve (Karvonen). */
    HEART_RATE_RESERVE_FIVE,

    /** Seiler's three-zone model: below VT1, between VT1 and VT2, above VT2. */
    POLARIZED_THREE,
}
