package app.pacinglab.core.model

/**
 * A numeric stream (speed, cadence, power, ...) in strictly increasing time order.
 *
 * @property times epoch milliseconds.
 * @property values one value per timestamp, in the stream's unit (speed: m/s).
 */
class SampleSeries(val times: LongArray, val values: DoubleArray) {
    init {
        require(times.size == values.size) { "times (${times.size}) and values (${values.size}) differ in length" }
        for (i in 1 until times.size) {
            require(times[i] > times[i - 1]) { "Timestamps must be strictly increasing (index $i)" }
        }
    }

    val size: Int get() = times.size

    fun isEmpty(): Boolean = times.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is SampleSeries && times.contentEquals(other.times) && values.contentEquals(other.values)

    override fun hashCode(): Int = 31 * times.contentHashCode() + values.contentHashCode()

    override fun toString(): String = "SampleSeries(size=$size)"

    companion object {
        val EMPTY = SampleSeries(LongArray(0), DoubleArray(0))
    }
}
