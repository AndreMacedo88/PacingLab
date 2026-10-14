package app.pacinglab.core.model

/**
 * Heart rate samples in strictly increasing time order.
 *
 * Stored as parallel primitive arrays because a single run can hold several thousand samples
 * and analyses iterate over them many times.
 *
 * @property times sample timestamps, epoch milliseconds.
 * @property bpm heart rate at each timestamp.
 */
class HeartRateSeries(val times: LongArray, val bpm: IntArray) {
    init {
        require(times.size == bpm.size) { "times (${times.size}) and bpm (${bpm.size}) differ in length" }
        for (i in 1 until times.size) {
            require(times[i] > times[i - 1]) { "Timestamps must be strictly increasing (index $i)" }
        }
    }

    val size: Int get() = times.size

    fun isEmpty(): Boolean = times.isEmpty()

    override fun equals(other: Any?): Boolean = other is HeartRateSeries && times.contentEquals(other.times) && bpm.contentEquals(other.bpm)

    override fun hashCode(): Int = 31 * times.contentHashCode() + bpm.contentHashCode()

    override fun toString(): String = "HeartRateSeries(size=$size)"

    companion object {
        val EMPTY = HeartRateSeries(LongArray(0), IntArray(0))

        /** Builds a series from (epoch millis, bpm) pairs, which must already be in time order. */
        fun of(vararg samples: Pair<Long, Int>): HeartRateSeries = HeartRateSeries(
            times = LongArray(samples.size) { samples[it].first },
            bpm = IntArray(samples.size) { samples[it].second },
        )
    }
}
