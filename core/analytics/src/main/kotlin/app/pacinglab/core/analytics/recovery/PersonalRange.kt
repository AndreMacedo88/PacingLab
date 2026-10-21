package app.pacinglab.core.analytics.recovery

import java.time.LocalDate
import kotlin.math.sqrt

/**
 * The athlete's usual range for a nightly measure on a given date: the mean of the [WINDOW_DAYS] days
 * before it plus or minus one standard deviation, at least [minHalfWidth] either way. The range follows
 * the athlete from day to day, the way watch apps draw "your typical range". Null with fewer than
 * [MIN_VALUES] values in the window.
 */
object PersonalRange {
    const val WINDOW_DAYS = 30L
    const val MIN_VALUES = 7

    fun of(values: Map<LocalDate, Double>, date: LocalDate, minHalfWidth: Double = 0.0): ClosedFloatingPointRange<Double>? {
        val window = values.filterKeys { it < date && it >= date.minusDays(WINDOW_DAYS) }.values
        if (window.size < MIN_VALUES) return null
        val mean = window.average()
        val sd = sqrt(window.sumOf { (it - mean) * (it - mean) } / (window.size - 1))
        val half = maxOf(sd, minHalfWidth)
        return mean - half..mean + half
    }
}
