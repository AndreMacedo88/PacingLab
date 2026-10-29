package app.pacinglab.core.analytics.performance

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Fitbit's cardio fitness value of one day with the variance it reports for it (Google Health API). */
data class FitbitDailyVo2Max(val date: LocalDate, val value: Double, val variance: Double)

/** A run measurement Fitbit folded into its daily value, recovered from the daily series. */
data class FitbitVo2MaxUpdate(val date: LocalDate, val value: Double, val variance: Double)

/**
 * Fitbit's daily VO2max is the state of its own Kalman filter: on days without a run the variance grows
 * by [PROCESS_VARIANCE_PER_DAY] (exactly, in the owner's 2025-2026 data), and the day after a run the
 * value moves and the variance drops. Inverting one update gives back the run's measurement z and its
 * noise R:
 *
 *     P- = P(before) + 0.05 days,  K = 1 - P(after) / P-,  z = x(before) + (x(after) - x(before)) / K,
 *     R = P- P(after) / (P- - P(after)).
 *
 * The z found this way equals Fitbit's per-run value in Health Connect, so the daily series adds no new
 * runs where Health Connect has them, but it says how much Fitbit trusted each one, which runs it left
 * out, and it reaches back to runs from before Fitbit wrote to Health Connect.
 */
object FitbitVo2Max {
    const val PROCESS_VARIANCE_PER_DAY = 0.05

    /** A drop smaller than this is rounding, not an update. */
    private const val MIN_DROP = 0.01

    /** Relative weights are kept within these bounds, so one run never outweighs a dozen. */
    private val WEIGHT_RANGE = 0.25..4.0

    /** The measurements behind [days] (any order), each dated the day before the update showed. */
    fun updates(days: List<FitbitDailyVo2Max>): List<FitbitVo2MaxUpdate> =
        days.sortedBy { it.date }.zipWithNext().mapNotNull { (before, after) ->
            val gap = ChronoUnit.DAYS.between(before.date, after.date)
            if (gap < 1) return@mapNotNull null
            val predicted = before.variance + PROCESS_VARIANCE_PER_DAY * gap
            val drop = predicted - after.variance
            if (drop < MIN_DROP || after.variance <= 0) return@mapNotNull null
            val gain = drop / predicted
            FitbitVo2MaxUpdate(
                date = after.date.minusDays(1),
                value = before.value + (after.value - before.value) / gain,
                variance = predicted * after.variance / drop,
            )
        }

    /**
     * Fitbit's VO2max values for the trend: [recorded] (Fitbit's per-run values from Health Connect)
     * combined with what [daily] reveals. A run measurement found in the daily series replaces the
     * recorded value of its day and is weighted by how much Fitbit trusted it, relative to its typical
     * run (the trend still learns Fitbit's overall scatter). A recorded value that Fitbit's own series
     * passed over (the two days after it are known and show no update) is left out, as Fitbit left it
     * out. Without [daily] the recorded values are returned unchanged.
     */
    fun points(recorded: List<Vo2MaxPoint>, daily: List<FitbitDailyVo2Max>): List<Vo2MaxPoint> {
        val updates = updates(daily)
        if (updates.isEmpty()) return recorded
        val typical = updates.map { it.variance }.sorted()[updates.size / 2]
        val fromDaily = updates.map { update ->
            Vo2MaxPoint(update.date, update.value, Vo2MaxSource.FITBIT, weight = (typical / update.variance).coerceIn(WEIGHT_RANGE))
        }
        val updateDates = updates.map { it.date }.toSet()
        val known = daily.map { it.date }.toSet()
        val kept = recorded.filter { point ->
            val nearUpdate = (-1L..1L).any { point.date.plusDays(it) in updateDates }
            val passedOver = point.date.plusDays(1) in known && point.date.plusDays(2) in known
            !nearUpdate && !passedOver
        }
        return (kept + fromDaily).sortedBy { it.date }
    }
}
