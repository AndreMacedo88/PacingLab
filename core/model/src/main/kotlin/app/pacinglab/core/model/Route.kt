package app.pacinglab.core.model

/**
 * GPS track in strictly increasing time order. Optional per-point values use `NaN` when absent, which
 * keeps every column a primitive array.
 *
 * @property times epoch milliseconds.
 * @property altitudes metres above sea level, or `NaN`.
 * @property accuracies horizontal accuracy in metres, or `NaN`.
 */
class Route(
    val times: LongArray,
    val latitudes: DoubleArray,
    val longitudes: DoubleArray,
    val altitudes: DoubleArray,
    val accuracies: DoubleArray,
) {
    init {
        val n = times.size
        require(latitudes.size == n && longitudes.size == n && altitudes.size == n && accuracies.size == n) {
            "All route columns must have $n entries"
        }
        for (i in 1 until n) {
            require(times[i] > times[i - 1]) { "Timestamps must be strictly increasing (index $i)" }
        }
    }

    val size: Int get() = times.size

    fun isEmpty(): Boolean = times.isEmpty()

    override fun equals(other: Any?): Boolean = other is Route &&
        times.contentEquals(other.times) &&
        latitudes.contentEquals(other.latitudes) &&
        longitudes.contentEquals(other.longitudes) &&
        altitudes.contentEquals(other.altitudes) &&
        accuracies.contentEquals(other.accuracies)

    override fun hashCode(): Int {
        var result = times.contentHashCode()
        result = 31 * result + latitudes.contentHashCode()
        result = 31 * result + longitudes.contentHashCode()
        result = 31 * result + altitudes.contentHashCode()
        result = 31 * result + accuracies.contentHashCode()
        return result
    }

    override fun toString(): String = "Route(size=$size)"

    companion object {
        val EMPTY = Route(LongArray(0), DoubleArray(0), DoubleArray(0), DoubleArray(0), DoubleArray(0))
    }
}
