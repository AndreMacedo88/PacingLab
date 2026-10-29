package app.pacinglab.core.analytics.recovery

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SleepDebtLevel {
    NONE,
    LOW,
    MODERATE,
    HIGH,
}

/**
 * Sleep missed against need over the last two weeks, as Oura and WHOOP show it: each night's shortfall
 * (need minus time asleep) weighted from 1 for last night down to 1/14 for two weeks ago, because
 * recent sleep loss weighs on today more. A night with more sleep than needed pays back half its surplus:
 * recovery sleep restores performance only partly after restriction (Belenky 2003; Banks 2010). Nights
 * without a record are skipped, not counted as lost. Levels follow Oura: none, low under 2 hours, moderate
 * 2 to 5 hours, high over 5.
 *
 * @property millis the debt, never below zero.
 * @property nights nights it rests on.
 */
data class SleepDebt(val millis: Long, val nights: Int) {
    val level: SleepDebtLevel
        get() = when {
            millis < NONE_BELOW -> SleepDebtLevel.NONE
            millis < MODERATE_FROM -> SleepDebtLevel.LOW
            millis <= HIGH_ABOVE -> SleepDebtLevel.MODERATE
            else -> SleepDebtLevel.HIGH
        }

    companion object {
        const val WINDOW_DAYS = 14

        /** Nights the two weeks need, as Oura asks for. */
        const val MIN_NIGHTS = 5

        private const val HOUR = 3_600_000L
        private const val NONE_BELOW = HOUR / 4
        private const val MODERATE_FROM = 2 * HOUR
        private const val HIGH_ABOVE = 5 * HOUR
        private const val SURPLUS_WEIGHT = 0.5

        /** The debt on every morning from the first with enough nights; [needs] by morning. */
        fun all(nights: List<Night>, needs: Map<LocalDate, Long>): Map<LocalDate, SleepDebt> {
            val sorted = nights.sortedBy { it.date }
            return sorted.mapNotNull { night -> of(sorted, needs, night.date)?.let { night.date to it } }.toMap()
        }

        /** The debt going into [date]; null with fewer than [MIN_NIGHTS] nights in the two weeks. */
        fun of(nights: List<Night>, needs: Map<LocalDate, Long>, date: LocalDate): SleepDebt? {
            val window = nights.filter { it.date <= date && ChronoUnit.DAYS.between(it.date, date) < WINDOW_DAYS && needs[it.date] != null }
            if (window.size < MIN_NIGHTS) return null
            val balance = window.sumOf { night ->
                val weight = (WINDOW_DAYS - ChronoUnit.DAYS.between(night.date, date)).toDouble() / WINDOW_DAYS
                val shortfall = (needs.getValue(night.date) - night.asleepMillis).toDouble()
                weight * if (shortfall < 0) SURPLUS_WEIGHT * shortfall else shortfall
            }
            return SleepDebt(balance.toLong().coerceAtLeast(0), window.size)
        }
    }
}
