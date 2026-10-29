package app.pacinglab.core.analytics.performance

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** One run for [MarathonShape]: its date and distance. */
data class RunDistance(val date: LocalDate, val meters: Double)

/**
 * How well the running of recent months prepares for long races, after Runalyze's "marathon shape"
 * (Runalyze source, MarathonShape and TrainingRepository.calculateMarathonShape; Runalyze calls it a
 * heuristic built from typical marathon plans, not a validated model). 100 % is the training a
 * marathon at the athlete's VO2max needs.
 *
 * @property percent the shape: two thirds from weekly distance, one third from long runs.
 * @property weeklyKm average weekly distance over the last [weeklyDays] days.
 * @property targetWeeklyKm the weekly distance a marathon at this VO2max asks for: VO2max^1.135.
 * @property longRunKm the long run a marathon at this VO2max asks for once a week: 12 ln(VO2max / 4).
 * @property longRuns runs over [MarathonShapes.LONG_RUN_KM] in the last [MarathonShapes.LONG_RUN_DAYS] days.
 * @property longRunShare the long runs' points as a share of one target long run a week.
 */
data class MarathonShape(
    val percent: Double,
    val weeklyKm: Double,
    val targetWeeklyKm: Double,
    val longRunKm: Double,
    val longRuns: Int,
    val longRunShare: Double,
    val weeklyDays: Int,
)

object MarathonShapes {
    const val LONG_RUN_KM = 13.0
    const val LONG_RUN_DAYS = 70
    private const val WEEKLY_DAYS = 182
    private const val MIN_WEEKLY_DAYS = 70
    private const val WEEKLY_WEIGHT = 0.67
    private const val LONG_RUN_WEIGHT = 0.33
    private const val MIN_VO2MAX = 25.0

    /**
     * The shape on [until] for an athlete at [vo2max], from [runs] (any order). Weekly distance is
     * averaged over the last 182 days, or since the first run when the history is shorter (at least 70
     * days). Each run over 13 km in the last 70 days earns ((km - 13) / (target - 13))^2 points, weighted
     * from 2 for today down to 0 seventy days ago, so one target long run a week holds the long-run part
     * at 100 %.
     */
    fun of(runs: List<RunDistance>, vo2max: Double, until: LocalDate): MarathonShape {
        val vo2 = max(vo2max, MIN_VO2MAX)
        val targetWeekly = vo2.pow(1.135)
        val longRunKm = 12 * ln(vo2 / 4)
        val past = runs.filter { !it.date.isAfter(until) }
        val first = past.minOfOrNull { it.date }
        val sinceFirst = first?.let { ChronoUnit.DAYS.between(it, until).toInt() } ?: WEEKLY_DAYS
        val weeklyDays = max(min(WEEKLY_DAYS, sinceFirst), MIN_WEEKLY_DAYS)
        val weeklyKm = past.filter { ChronoUnit.DAYS.between(it.date, until) < weeklyDays }.sumOf { it.meters } / 1_000 * 7 / weeklyDays
        val long = past.filter { it.meters / 1_000 > LONG_RUN_KM && ChronoUnit.DAYS.between(it.date, until) < LONG_RUN_DAYS }
        val points = long.sumOf { run ->
            val daysAgo = ChronoUnit.DAYS.between(run.date, until)
            val weight = 2 - 2.0 / LONG_RUN_DAYS * daysAgo
            weight * ((run.meters / 1_000 - LONG_RUN_KM) / (longRunKm - LONG_RUN_KM)).pow(2)
        }
        val longRunShare = points * 7 / LONG_RUN_DAYS
        return MarathonShape(
            percent = 100 * (weeklyKm / targetWeekly * WEEKLY_WEIGHT + longRunShare * LONG_RUN_WEIGHT),
            weeklyKm = weeklyKm,
            targetWeeklyKm = targetWeekly,
            longRunKm = longRunKm,
            longRuns = long.size,
            longRunShare = longRunShare,
            weeklyDays = weeklyDays,
        )
    }

    /** The shape a race over [meters] asks for to be run as fast as VO2max allows: km^1.23 (a marathon about 100 %). */
    fun requiredFor(meters: Double): Double = (meters / 1_000).pow(1.23)

    /**
     * The share of VO2max a race over [meters] can be run at with [shapePercent]: 1 when the shape is
     * enough, falling to 0.6 as it runs short (Runalyze's prognosis adjustment).
     */
    fun factor(meters: Double, shapePercent: Double): Double {
        val shortfall = max(0.0, 1 - (requiredFor(meters) - shapePercent) / 100)
        return min(1.0, 0.6 + 0.4 * shortfall)
    }
}
