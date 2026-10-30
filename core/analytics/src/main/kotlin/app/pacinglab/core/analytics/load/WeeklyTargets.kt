package app.pacinglab.core.analytics.load

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The weekly load that builds fitness without a jump the body is not used to: from 0.8 to 1.3 times the
 * load of a usual week at the current fitness (7 x fitness, the chronic load), the acute to chronic
 * workload ratio's "sweet spot" (Gabbett 2016; the ratio's use for injury prediction is disputed,
 * Impellizzeri 2020, so it is a guide, not a rule). Below the band fitness falls; well above it the
 * week is a jump.
 *
 * @property fitness fitness on the Monday the week starts.
 */
data class LoadBand(val weekStart: LocalDate, val fitness: Double, val low: Double, val high: Double)

/**
 * Running distance in a week against the athlete's tolerance: up to 30 % more than the average of the
 * four weeks before. Runners who raised their weekly distance by more than 30 % had more overuse
 * injuries than those who stayed within 10 % (Nielsen 2014), and in half-marathon training increases of
 * 20 to 60 % came with more injuries in the first weeks (Damsted 2019); the evidence is limited, so it is
 * a caution, not a limit. Garmin's running tolerance rests on the same idea with its own, unpublished
 * rule.
 *
 * @property usualKm the average weekly running distance of the four weeks before.
 * @property toleranceKm [usualKm] plus 30 %.
 */
data class RunningWeek(val weekStart: LocalDate, val km: Double, val usualKm: Double?, val toleranceKm: Double?) {
    val above: Boolean get() = toleranceKm != null && km > toleranceKm
}

object WeeklyTargets {
    const val LOW_RATIO = 0.8
    const val HIGH_RATIO = 1.3
    const val TOLERANCE = 1.3
    const val USUAL_WEEKS = 4

    /** Fitness below this (a stress score of 5 a day) gives no band: there is no habit to build on. */
    const val MIN_FITNESS = 5.0

    /** The load band of each of [weeks] (Mondays), from the fitness model [series]; weeks before it have none. */
    fun loadBands(series: List<FormDay>, weeks: List<LocalDate>): Map<LocalDate, LoadBand> {
        val byDate = series.associateBy { it.date }
        return weeks.mapNotNull { monday ->
            // Fitness at the start of the week: the end of the Sunday before.
            val fitness = byDate[monday.minusDays(1)]?.fitness ?: return@mapNotNull null
            if (fitness < MIN_FITNESS) return@mapNotNull null
            monday to LoadBand(monday, fitness, LOW_RATIO * 7 * fitness, HIGH_RATIO * 7 * fitness)
        }.toMap()
    }

    /**
     * The last [count] calendar weeks (Monday to Sunday) up to the one holding [until], newest first, each
     * with its running distance from [runs] (date to metres) and its tolerance. A week whose four weeks
     * before hold no running at all has no tolerance.
     */
    fun runningWeeks(runs: List<Pair<LocalDate, Double>>, until: LocalDate, count: Int): List<RunningWeek> {
        val lastMonday = until.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun km(monday: LocalDate) = runs.filter { (date, _) -> date >= monday && date < monday.plusWeeks(1) }.sumOf { it.second } / 1_000
        return List(count) { i ->
            val monday = lastMonday.minusWeeks(i.toLong())
            val usual = (1..USUAL_WEEKS).map { km(monday.minusWeeks(it.toLong())) }.average().takeIf { it > 0 }
            RunningWeek(monday, km(monday), usual, usual?.let { it * TOLERANCE })
        }
    }
}
