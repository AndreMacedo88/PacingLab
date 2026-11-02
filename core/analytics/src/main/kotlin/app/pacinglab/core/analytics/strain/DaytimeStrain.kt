package app.pacinglab.core.analytics.strain

import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.model.StillHeartRate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a day's strain compares with the athlete's usual days. */
enum class StrainLevel { CALM, USUAL, RAISED, HIGH }

/** Something in the athlete's data that raises daytime heart rate besides stress. */
enum class StrainCause {
    /** Two or more vitals off their range the night before or after: often an illness starting. */
    VITALS_OFF,

    /** A hard session that day or the day before. */
    HARD_TRAINING,

    /** A poor night before the day. */
    POOR_SLEEP,
}

/**
 * One day's strain.
 *
 * @property excessBpm how far the day's still heart rate ran above the athlete's usual for the same hours.
 * @property stillMinutes the still minutes it rests on.
 * @property score 50 for a usual day, 10 more for each typical day-to-day spread above it; null until
 *   [DaytimeStrain.MIN_SCALE_DAYS] days set the scale.
 */
data class StrainDay(val date: LocalDate, val excessBpm: Double, val stillMinutes: Int, val score: Int?, val level: StrainLevel?)

/**
 * Daytime strain: heart rate that movement does not explain, the "additional heart rate" of ambulatory
 * psychophysiology (Myrtek 2004), measured in the still, awake minutes of a day ([StillMinutes]).
 *
 * For each hour of the day the athlete's usual still heart rate is the median of that hour's mean over
 * the [BASELINE_DAYS] days before (at least [MIN_HOUR_DAYS] days with [MIN_HOUR_MINUTES] still minutes),
 * which takes out the daily rhythm and habits such as sitting at work. A day's excess is its still
 * minutes' mean distance from those hourly baselines, from [MIN_DAY_MINUTES] still minutes on. Its score
 * places the excess among the athlete's own days, as a T-score does: 50 plus 10 for each robust standard
 * deviation (1.4826 times the median absolute deviation, at least [MIN_SPREAD] bpm) above the median of
 * the [BASELINE_DAYS] days before, from [MIN_SCALE_DAYS] of them, clamped to 0 to 100.
 *
 * Psychological stress raises it, but so do illness, heat, caffeine, a large meal, short sleep, hard
 * training the day before and standing rather than sitting; heart rate alone cannot tell these apart.
 * [causes] names those the data can see.
 */
object DaytimeStrain {
    const val BASELINE_DAYS = 28L
    const val MIN_HOUR_DAYS = 7
    const val MIN_HOUR_MINUTES = 10
    const val MIN_DAY_MINUTES = 120
    const val MIN_SCALE_DAYS = 14
    const val MIN_SPREAD = 1.5

    const val USUAL_FROM = 40
    const val RAISED_FROM = 60
    const val HIGH_FROM = 70

    /** Robust standard deviation per median absolute deviation, for normally distributed values. */
    private const val MAD_SCALE = 1.4826

    /** A day's training load counts as hard from this, and from [HARD_RATIO] times fitness. */
    const val HARD_LOAD = 60.0
    const val HARD_RATIO = 1.5

    /** The strain of every day with enough still minutes, oldest first. */
    fun days(summaries: List<StillHeartRate>): List<StrainDay> {
        val byDate = summaries.associateBy { it.date }
        val excess = summaries.sortedBy { it.date }.mapNotNull { day -> excessOf(day, byDate)?.let { day.date to it } }
        val values = excess.associate { (date, e) -> date to e.first }
        return excess.map { (date, e) ->
            val prior = values.filterKeys { it < date && it >= date.minusDays(BASELINE_DAYS) }.values.sorted()
            val score = if (prior.size >= MIN_SCALE_DAYS) {
                val center = NightlyRange.quantile(prior, 0.5)
                val spread = maxOf(MAD_SCALE * NightlyRange.quantile(prior.map { abs(it - center) }.sorted(), 0.5), MIN_SPREAD)
                (50 + 10 * (e.first - center) / spread).coerceIn(0.0, 100.0).roundToInt()
            } else {
                null
            }
            StrainDay(date, e.first, e.second, score, score?.let(::levelOf))
        }
    }

    fun levelOf(score: Int): StrainLevel = when {
        score >= HIGH_FROM -> StrainLevel.HIGH
        score >= RAISED_FROM -> StrainLevel.RAISED
        score >= USUAL_FROM -> StrainLevel.USUAL
        else -> StrainLevel.CALM
    }

    /** The usual still heart rate in [hour] before [date]; null with too few days. */
    fun baseline(hour: Int, date: LocalDate, byDate: Map<LocalDate, StillHeartRate>): Double? {
        val means = (1..BASELINE_DAYS).mapNotNull { back ->
            byDate[date.minusDays(back)]?.takeIf { it.minutes[hour] >= MIN_HOUR_MINUTES }?.mean(hour)
        }.sorted()
        return if (means.size < MIN_HOUR_DAYS) null else NightlyRange.quantile(means, 0.5)
    }

    /** The mean excess over the hourly baselines and the minutes it covers; null below [MIN_DAY_MINUTES]. */
    private fun excessOf(day: StillHeartRate, byDate: Map<LocalDate, StillHeartRate>): Pair<Double, Int>? {
        var excess = 0.0
        var minutes = 0
        for (hour in 0 until StillHeartRate.HOURS) {
            val n = day.minutes[hour]
            if (n == 0) continue
            val usual = baseline(hour, day.date, byDate) ?: continue
            excess += day.sums[hour] - n * usual
            minutes += n
        }
        return if (minutes < MIN_DAY_MINUTES) null else excess / minutes to minutes
    }

    /**
     * What else could explain strain on [date]: vitals off the night before or after it ([alerts], by
     * morning), hard training that day or the day before ([hardTraining]), a poor night before it
     * ([poorSleep], by morning).
     */
    fun causes(date: LocalDate, alerts: Set<LocalDate>, hardTraining: Set<LocalDate>, poorSleep: Set<LocalDate>): List<StrainCause> =
        buildList {
            if (date in alerts || date.plusDays(1) in alerts) add(StrainCause.VITALS_OFF)
            if (date in hardTraining || date.minusDays(1) in hardTraining) add(StrainCause.HARD_TRAINING)
            if (date in poorSleep) add(StrainCause.POOR_SLEEP)
        }

    /** Days whose load reached [HARD_LOAD] and [HARD_RATIO] times the fitness going into them. */
    fun hardTrainingDays(form: List<FormDay>): Set<LocalDate> {
        var fitness: Double? = null
        val hard = mutableSetOf<LocalDate>()
        for (day in form) {
            val before = fitness ?: day.fitness
            if (day.load >= HARD_LOAD && day.load >= HARD_RATIO * before) hard += day.date
            fitness = day.fitness
        }
        return hard
    }
}
