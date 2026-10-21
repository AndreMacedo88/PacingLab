package app.pacinglab.core.analytics.load

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * One day of the fitness-fatigue model.
 *
 * @property load training load of the day (sum of the stress scores of its workouts).
 * @property fitness chronic training load at the end of the day: load averaged over about six weeks.
 * @property fatigue acute training load at the end of the day: load averaged over about a week.
 * @property form fitness minus fatigue at the start of the day, before its training: how fresh the
 *   athlete is going into the day.
 */
data class FormDay(val date: LocalDate, val load: Double, val fitness: Double, val fatigue: Double, val form: Double)

/**
 * Banister's impulse-response model in the form popularised by Coggan: fitness and fatigue are
 * exponentially weighted averages of daily load with time constants of 42 and 7 days.
 */
object FitnessModel {
    const val FITNESS_DAYS = 42
    const val FATIGUE_DAYS = 7

    /**
     * Every day from the first day in [dailyLoads] to [until], days without training counting as zero.
     * Both averages start at the mean daily load of the first [FITNESS_DAYS] days (or as many as there
     * are), so the series does not begin with an artificial build-up from zero.
     */
    fun series(dailyLoads: Map<LocalDate, Double>, until: LocalDate): List<FormDay> {
        val first = dailyLoads.keys.minOrNull() ?: return emptyList()
        if (until < first) return emptyList()
        val days = ChronoUnit.DAYS.between(first, until).toInt() + 1
        val loads = DoubleArray(days) { dailyLoads[first.plusDays(it.toLong())] ?: 0.0 }
        val seed = loads.take(minOf(days, FITNESS_DAYS)).average()
        val fitnessRate = 1 - exp(-1.0 / FITNESS_DAYS)
        val fatigueRate = 1 - exp(-1.0 / FATIGUE_DAYS)
        var fitness = seed
        var fatigue = seed
        return List(days) { i ->
            val form = fitness - fatigue
            fitness += (loads[i] - fitness) * fitnessRate
            fatigue += (loads[i] - fatigue) * fatigueRate
            FormDay(first.plusDays(i.toLong()), loads[i], fitness, fatigue, form)
        }
    }

    /** Change in fitness over the 7 days up to [index] of [series]; null for the first week. */
    fun rampRate(series: List<FormDay>, index: Int): Double? = if (index < 7) null else series[index].fitness - series[index - 7].fitness
}

/**
 * Foster's training monotony and strain for the 7 days of [loads]: monotony is mean daily load divided
 * by its standard deviation, strain the week's load times monotony (Foster 1998). Null when the load
 * does not vary at all, including a week without training.
 */
data class WeekStrain(val load: Double, val monotony: Double, val strain: Double) {
    companion object {
        fun of(loads: DoubleArray): WeekStrain? {
            require(loads.size == 7) { "A week has 7 daily loads, got ${loads.size}" }
            val mean = loads.average()
            val sd = sqrt(loads.sumOf { (it - mean) * (it - mean) } / loads.size)
            if (sd == 0.0) return null
            val monotony = mean / sd
            return WeekStrain(load = loads.sum(), monotony = monotony, strain = loads.sum() * monotony)
        }
    }
}
