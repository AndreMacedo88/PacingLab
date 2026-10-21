package app.pacinglab.core.analytics.series

/**
 * One value per second for [seconds] seconds from [startMillis]: each sample of [times]/[values] holds
 * until the next one, for at most [maxHoldMillis]; seconds without a sample are NaN. [times] must be
 * ascending.
 */
internal fun secondGrid(times: LongArray, values: DoubleArray, startMillis: Long, seconds: Int, maxHoldMillis: Long = 5_000L): DoubleArray {
    val out = DoubleArray(seconds) { Double.NaN }
    if (times.isEmpty()) return out
    var j = 0
    for (s in 0 until seconds) {
        val t = startMillis + s * 1_000L
        while (j + 1 < times.size && times[j + 1] <= t) j++
        if (times[j] <= t && t - times[j] <= maxHoldMillis) out[s] = values[j]
    }
    return out
}
