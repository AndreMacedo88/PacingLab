package app.pacinglab.core.model

import java.time.LocalDate

/**
 * Heart rate in the still, awake minutes of one day, added up by hour of the day; only these sums are
 * kept, not the day's heart rate. Which minutes count is decided in core/analytics (StillMinutes).
 *
 * @property minutes still minutes in each hour of the day, 0 to 23 by the local clock.
 * @property sums the sum of the minutes' mean heart rates in each hour.
 */
class StillHeartRate(val date: LocalDate, val minutes: IntArray, val sums: DoubleArray) {
    init {
        require(minutes.size == HOURS && sums.size == HOURS) { "A day has $HOURS hours" }
    }

    val totalMinutes: Int get() = minutes.sum()

    /** Mean still heart rate in [hour]; null without still minutes then. */
    fun mean(hour: Int): Double? = if (minutes[hour] == 0) null else sums[hour] / minutes[hour]

    override fun equals(other: Any?): Boolean =
        other is StillHeartRate && date == other.date && minutes.contentEquals(other.minutes) && sums.contentEquals(other.sums)

    override fun hashCode(): Int = 31 * date.hashCode() + minutes.contentHashCode()

    override fun toString(): String = "StillHeartRate($date, $totalMinutes min)"

    companion object {
        const val HOURS = 24
    }
}
