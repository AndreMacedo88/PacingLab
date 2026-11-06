package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.analytics.stats.ShrinkageRegression
import app.pacinglab.core.model.SleepStageType
import app.pacinglab.core.model.home.NightClimate
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * What the factors are compared with.
 *
 * @property minimum the smallest effect worth showing as clear, in the outcome's own units: a point of
 *   sleep score, a tenth of a step on the 1 to 5 rating.
 */
enum class SleepOutcome(val minimum: Double) {
    SCORE(1.0),
    RESTED(0.1),
}

/**
 * Where a factor sits relative to the night. Factors before the night are things the day brings; their
 * effects are total effects, whatever path they take. The bedroom's climate surrounds the night from
 * outside, so it is fitted together with them. During the night are the sleep's own measures (for how
 * rested the athlete feels only). Body measures are taken during the same night and partly shaped by the
 * sleep itself, so they mark nights rather than cause them.
 */
enum class FactorGroup {
    BEFORE,
    BEDROOM,
    NIGHT,
    BODY,
    ;

    /** Fitted in the first model, without the night's own measures, so that its effects are total effects. */
    val outside: Boolean get() = this == BEFORE || this == BEDROOM
}

/** The unit a factor's typical change is given in. [NONE] marks a yes-or-no factor. */
enum class FactorUnit { STRESS, MINUTES, FORM, PERCENT, BPM, CELSIUS, PPM, NONE }

/**
 * A candidate influence on a night. Every value grows in the direction its name reads: more load, later
 * sleep, more form, a longer night before, a free day, more time asleep or awake, more deep or REM sleep,
 * deep sleep shortly before waking, higher HRV or sleeping heart rate, warmer skin.
 */
enum class SleepFactor(val group: FactorGroup, val unit: FactorUnit, val outcomes: Set<SleepOutcome> = SleepOutcome.entries.toSet()) {
    /** Stress score of the training that ended in the 24 hours before falling asleep. */
    TRAINING_LOAD(FactorGroup.BEFORE, FactorUnit.STRESS),

    /** Training with a stress score of at least [SleepFactors.LATE_STRESS] ended less than four hours before falling asleep. */
    LATE_TRAINING(FactorGroup.BEFORE, FactorUnit.NONE),

    /** Form (fitness minus fatigue) going into the day before the night. */
    FORM(FactorGroup.BEFORE, FactorUnit.FORM),

    /** Clock time of falling asleep. */
    BEDTIME(FactorGroup.BEFORE, FactorUnit.MINUTES),

    /** Time asleep the night before, against the sleep need. */
    PREVIOUS_NIGHT(FactorGroup.BEFORE, FactorUnit.MINUTES),

    /** The morning after the night is one of the athlete's free days. */
    FREE_DAY(FactorGroup.BEFORE, FactorUnit.NONE),

    /** Daytime strain of the day before the night: still heart rate above its usual ([app.pacinglab.core.analytics.strain.DaytimeStrain]). */
    DAYTIME_STRAIN(FactorGroup.BEFORE, FactorUnit.BPM),

    /** Mean bedroom temperature from falling asleep to the final awakening, from Home Assistant. */
    BEDROOM_TEMPERATURE(FactorGroup.BEDROOM, FactorUnit.CELSIUS),

    /** Mean relative humidity of the bedroom over the night. */
    BEDROOM_HUMIDITY(FactorGroup.BEDROOM, FactorUnit.PERCENT),

    /** Mean CO2 in the bedroom over the night, in ppm: how well the room was aired (Strøm-Tejsen 2016). */
    BEDROOM_CO2(FactorGroup.BEDROOM, FactorUnit.PPM),

    /** Time asleep against the sleep need. */
    TIME_ASLEEP(FactorGroup.NIGHT, FactorUnit.MINUTES, setOf(SleepOutcome.RESTED)),

    /** Time awake after falling asleep. */
    AWAKE(FactorGroup.NIGHT, FactorUnit.MINUTES, setOf(SleepOutcome.RESTED)),

    /**
     * Minutes of deep sleep. With time asleep in the same model, more deep sleep means less of the other
     * stages, so the effect is that of the night's make-up rather than its length.
     */
    DEEP_SLEEP(FactorGroup.NIGHT, FactorUnit.MINUTES, setOf(SleepOutcome.RESTED)),

    /** Minutes of REM sleep, likewise beyond time asleep. */
    REM_SLEEP(FactorGroup.NIGHT, FactorUnit.MINUTES, setOf(SleepOutcome.RESTED)),

    /** Deep sleep in the last [SleepFactors.DEEP_BEFORE_WAKING] before the final awakening, when sleep inertia is worst. */
    DEEP_BEFORE_WAKING(FactorGroup.NIGHT, FactorUnit.NONE, setOf(SleepOutcome.RESTED)),

    /** HRV against the median of the nights before, in per cent (100 × the log ratio). */
    HRV(FactorGroup.BODY, FactorUnit.PERCENT),

    /** Average sleeping heart rate against the median of the nights before. */
    SLEEPING_HR(FactorGroup.BODY, FactorUnit.BPM),

    /** Skin temperature against the watch app's baseline. */
    SKIN_TEMPERATURE(FactorGroup.BODY, FactorUnit.CELSIUS),
    ;

    val binary: Boolean get() = unit == FactorUnit.NONE
}

/** A workout as the sleep factors see it: when it ended and its stress score. */
data class TrainingBout(val end: Instant, val stressScore: Double)

/** One night in a factor's detail: the factor's value and the outcome with every other factor held at its centre. */
data class FactorPoint(val date: LocalDate, val value: Double, val adjusted: Double)

/**
 * A factor's estimated effect on an outcome, with every other factor of its model held equal.
 *
 * @property step the typical change the effect is given for, in the factor's unit: the middle half of its
 *   nights (interquartile range), rounded; 1 for a yes-or-no factor.
 * @property mean the posterior mean effect of [step] on the outcome.
 * @property low the 10th percentile of the effect.
 * @property high the 90th percentile; four times in five the effect lies between [low] and [high].
 * @property chance how likely the effect is to exceed the outcome's minimum in the direction of [mean].
 * @property clear [chance] is at least [SleepFactors.CLEAR_FROM] and the model predicts nights it has not
 *   seen better than the average does.
 * @property nights nights with a value for the factor.
 * @property yes for a yes-or-no factor, the nights it applied to.
 * @property slope the effect per unit of the factor, and [anchorValue] the outcome at [anchor], for the
 *   line through [points].
 */
data class FactorEffect(
    val factor: SleepFactor,
    val step: Double,
    val mean: Double,
    val low: Double,
    val high: Double,
    val chance: Double,
    val clear: Boolean,
    val nights: Int,
    val yes: Int? = null,
    val points: List<FactorPoint> = emptyList(),
    val slope: Double = 0.0,
    val anchor: Double = 0.0,
    val anchorValue: Double = 0.0,
)

/**
 * The factors of one outcome.
 *
 * @property nights the nights analysed, after leaving out those whose vitals were off.
 * @property excluded nights left out because two or more vitals were outside their range (often illness).
 * @property effects the factors with enough variety, before-the-night factors first, each group most
 *   certain first. Empty until [nights] reaches [SleepFactors.MIN_NIGHTS].
 * @property waiting factors without enough variety yet, such as a yes-or-no factor seen on too few nights.
 * @property predictive the before-the-night model predicts left-out nights better than their average.
 * @property spread the typical distance of a night from what the model expects (residual standard deviation).
 */
data class OutcomeFactors(
    val outcome: SleepOutcome,
    val nights: Int,
    val excluded: Int = 0,
    val effects: List<FactorEffect> = emptyList(),
    val waiting: List<SleepFactor> = emptyList(),
    val predictive: Boolean = false,
    val spread: Double? = null,
) {
    val learning: Boolean get() = nights < SleepFactors.MIN_NIGHTS
}

/** Where a night's expected restedness lies against the athlete's usual answer. */
enum class RestednessStanding { BELOW, USUAL, ABOVE }

/**
 * How rested the athlete can expect to feel after each night, on the 1 to 5 scale of the morning
 * question: the feeling-rested model of [SleepFactors] with every factor at once (the day before, the
 * bedroom, the night's own measures and the body's), applied to the night.
 *
 * It is judged only on mornings it has not seen: each rated morning is predicted from all the others
 * (leave one out), and the plain average of the answers is the bar to clear. Errors are root mean squared,
 * in steps of the rating, and the model beats the average only with at least a tenth less squared error
 * ([CLEARLY]): shrunk towards the average, a model of pure noise edges past it by a hair in about two
 * runs of three, but past this margin in under one in ten (simulations of 30 to 120 mornings). Sleep
 * factors shows the expected answer for the morning shown once it does.
 *
 * @property mornings the rated mornings the model learnt from.
 * @property expected the expected answer after each night, by morning, within 1 to 5. A rated morning
 *   gets its left-out prediction, so that the history shows what the model would have said without the
 *   answer; the other mornings get the prediction from every answer. Empty while learning or when the
 *   model predicts no better than the average.
 * @property error the typical distance of a left-out prediction from the answer.
 * @property averageError the same for the average of the other answers.
 * @property usual the athlete's usual answer, the model's centre.
 */
data class RestednessEstimate(
    val mornings: Int,
    val expected: Map<LocalDate, Double> = emptyMap(),
    val error: Double? = null,
    val averageError: Double? = null,
    val usual: Double? = null,
) {
    val learning: Boolean get() = mornings < SleepFactors.MIN_NIGHTS

    /** The model predicts mornings it has not seen clearly better than the average of the answers does. */
    val predictive: Boolean get() = error != null && averageError != null && error <= CLEARLY * averageError

    /** Where the morning of [date] is expected to lie against [usual]; null without an estimate for it. */
    fun standing(date: LocalDate): RestednessStanding? {
        val value = expected[date] ?: return null
        val centre = usual ?: return null
        return when {
            value >= centre + MARGIN -> RestednessStanding.ABOVE
            value <= centre - MARGIN -> RestednessStanding.BELOW
            else -> RestednessStanding.USUAL
        }
    }

    companion object {
        /** How far from the usual answer an expected value must lie to count as above or below it: a quarter of a step. */
        const val MARGIN = 0.25

        /** One error beats another at or below this share of it: a tenth less squared error. */
        val CLEARLY: Double = sqrt(0.9)

        const val LOWEST = 1.0
        const val HIGHEST = 5.0
    }
}

/**
 * What goes with better or worse nights, measured and felt: an observational analysis of the athlete's
 * own nights (N-of-1), with no diary.
 *
 * Each outcome (the sleep score, and how rested the athlete said they felt) is regressed on all its
 * factors at once with [ShrinkageRegression], so that each effect is the factor's own with the others
 * held equal, every effect is shrunk towards zero until the nights argue otherwise, and odd nights count
 * less. Nights with two or more vitals outside their range ([Vitals.alert], often the start of an illness)
 * are left out, so a cold does not pass for the effect of the training before it.
 *
 * Factors before the night are fitted alone, which gives their total effects (a late session that
 * shortens sleep shows its full cost). The night's own measures and the body measures are fitted on top
 * of them, so they show what goes with a night beyond what the day explains. An effect is clear when it
 * is at least [CLEAR_FROM] likely to exceed the outcome's minimum and its model predicts left-out nights
 * better than their average.
 *
 * All of this finds patterns, not causes: an unmeasured habit that travels with a factor (stress at work
 * with late nights, say) is credited to it. Only a self-experiment separates the two. Prediction needs
 * no causes, though: [restedness] combines every factor of the feeling-rested model into the answer
 * the athlete can expect after each night.
 */
data class SleepFactors(val score: OutcomeFactors, val rested: OutcomeFactors, val restedness: RestednessEstimate = RestednessEstimate(0)) {
    fun of(outcome: SleepOutcome): OutcomeFactors = if (outcome == SleepOutcome.SCORE) score else rested

    companion object {
        /** Nights (or rated mornings) before any estimate is made. */
        const val MIN_NIGHTS = 30

        /** Nights on each side a yes-or-no factor needs. */
        const val MIN_EACH_SIDE = 8

        /** Share of the analysed nights a factor needs a value on. */
        const val MIN_COVERAGE = 2.0 / 3

        /** The probability beyond the minimum effect that makes a factor clear: its 80 % range clears the minimum. */
        const val CLEAR_FROM = 0.9

        /** The most recent nights analysed; habits a year ago may no longer hold. */
        const val MAX_NIGHTS = 365

        /** Training counts as late when it ended this close to falling asleep (Leota 2025; Stutz 2019). */
        val LATE: Duration = Duration.ofHours(4)

        /** Stress score below which a late workout is too light to count (an easy half hour is about 25). */
        const val LATE_STRESS = 20.0

        /** Training load counts the workouts that ended this long before falling asleep. */
        val DAY: Duration = Duration.ofHours(24)

        val DEEP_BEFORE_WAKING: Duration = Duration.ofMinutes(30)

        private const val BASELINE_NIGHTS = 14
        private const val HRV_DAYS = 60L
        private const val HEART_RATE_DAYS = 30L
        private const val EVENING_MINUTE = 18 * 60
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * The factors of every scored night and rated morning.
         *
         * @param form form (fitness minus fatigue at the start of the day) by date.
         * @param freeDays the days of the week the athlete has free.
         * @param strain daytime strain by date, as the excess of still heart rate in bpm.
         * @param climate the bedroom over each night, by morning.
         */
        fun of(
            history: SleepHistory,
            rested: Map<LocalDate, Int>,
            training: List<TrainingBout>,
            form: Map<LocalDate, Double>,
            freeDays: Set<DayOfWeek>,
            strain: Map<LocalDate, Double> = emptyMap(),
            climate: Map<LocalDate, NightClimate> = emptyMap(),
        ): SleepFactors {
            val all = history.nights
            val latest = all.lastOrNull()?.date
            val recent = if (latest == null) emptyList() else all.filter { it.date > latest.minusDays(MAX_NIGHTS.toLong()) }
            val alerts = recent.filter { Vitals.of(all, it.date)?.alert == true }.map { it.date }.toSet()
            val bouts = training.sortedBy { it.end }
            val every = recent.map { night ->
                Row(night, values(night, all, history.needs, bouts, form, freeDays, strain) + climateValues(climate[night.date]))
            }
            val rows = every.filter { it.night.date !in alerts }
            fun analysed(outcome: SleepOutcome, value: (Night) -> Double?): Analysis {
                val usable = rows.mapNotNull { row -> value(row.night)?.let { row to it } }
                val excluded = recent.count { it.date in alerts && value(it) != null }
                return analyse(outcome, usable.map { it.first }, usable.map { it.second }.toDoubleArray(), excluded)
            }
            val feeling = analysed(SleepOutcome.RESTED) { night -> rested[night.date]?.toDouble() }
            return SleepFactors(
                score = analysed(SleepOutcome.SCORE) { night -> history.scores[night.date]?.score?.toDouble() }.factors,
                rested = feeling.factors,
                restedness = restedness(feeling, every),
            )
        }

        /**
         * The expected answer after each of [every] night from the feeling-rested model with all its
         * factors, judged against the average on left-out mornings.
         */
        private fun restedness(feeling: Analysis, every: List<Row>): RestednessEstimate {
            val rows = feeling.rows
            val y = feeling.y
            val fit = feeling.full
            if (rows.size < MIN_NIGHTS || fit == null) return RestednessEstimate(rows.size)
            val n = y.size
            val mean = y.average()
            val error = rootMeanSquare(y.indices.map { y[it] - fit.looFitted[it] })
            // Left out, the mean of the others misses by (y - mean) n / (n - 1).
            val averageError = rootMeanSquare(y.map { (it - mean) * n / (n - 1) })
            val estimate = RestednessEstimate(rows.size, error = error, averageError = averageError, usual = fit.intercept)
            if (!estimate.predictive) return estimate
            val leftOut = rows.indices.associate { rows[it].night.date to fit.looFitted[it] }
            val columns = feeling.columns
            val expected = every.associate { row ->
                val value =
                    leftOut[row.night.date] ?: fit.predict(DoubleArray(columns.size) { columns[it].scale(row.values[columns[it].factor]) })
                row.night.date to value.coerceIn(RestednessEstimate.LOWEST, RestednessEstimate.HIGHEST)
            }
            return estimate.copy(expected = expected)
        }

        private fun rootMeanSquare(values: List<Double>): Double = sqrt(values.sumOf { it * it } / values.size)

        private class Row(val night: Night, val values: Map<SleepFactor, Double?>)

        private class Column(val factor: SleepFactor, val center: Double, val step: Double, val raw: List<Double?>) {
            fun scaled(i: Int): Double = scale(raw[i])

            /** A value in the column's units; a missing one counts as the median. */
            fun scale(value: Double?): Double = value?.let { (it - center) / step } ?: 0.0
        }

        /**
         * One outcome's analysis: the [factors] shown, and for prediction the [rows] and answers [y] it
         * used, the [columns] that entered and the [full] model with all of them.
         */
        private class Analysis(
            val factors: OutcomeFactors,
            val rows: List<Row> = emptyList(),
            val y: DoubleArray = DoubleArray(0),
            val follows: BooleanArray = BooleanArray(0),
            val columns: List<Column> = emptyList(),
            val full: ShrinkageRegression? = null,
        )

        private fun analyse(outcome: SleepOutcome, rows: List<Row>, y: DoubleArray, excluded: Int): Analysis {
            if (rows.size < MIN_NIGHTS) return Analysis(OutcomeFactors(outcome, rows.size, excluded), rows, y)
            val candidates = SleepFactor.entries.filter { outcome in it.outcomes }
            val columns = mutableListOf<Column>()
            val waiting = mutableListOf<SleepFactor>()
            for (factor in candidates) {
                // A factor nothing measures, such as the bedroom without Home Assistant, is not waiting for anything.
                if (rows.none { it.values[factor] != null }) continue
                val column = column(factor, rows)
                if (column == null) waiting += factor else columns += column
            }
            val follows = BooleanArray(rows.size) { i ->
                i > 0 && ChronoUnit.DAYS.between(rows[i - 1].night.date, rows[i].night.date) == 1L
            }
            val before = columns.filter { it.factor.group.outside }
            val beforeFit = fit(before, rows.size, y, follows)
                ?: return Analysis(OutcomeFactors(outcome, rows.size, excluded, waiting = waiting), rows, y, follows)
            val beforePredictive = beforeFit.skill > 0
            val effects = effects(outcome, before, beforeFit, rows, y, beforePredictive).toMutableList()
            val extra = columns.filter { !it.factor.group.outside }
            val all = before + extra
            var full: ShrinkageRegression? = beforeFit
            if (extra.isNotEmpty()) {
                full = fit(all, rows.size, y, follows)?.also { model ->
                    // The night's measures must add to what the day already explains, not just beat the average.
                    val adds = model.skill > maxOf(beforeFit.skill, 0.0)
                    effects += effects(outcome, all, model, rows, y, adds).filter { !it.factor.group.outside }
                }
            }
            val spread = sqrt(
                y.indices.sumOf { (y[it] - beforeFit.fitted[it]) * (y[it] - beforeFit.fitted[it]) } / max(1, y.size - 1 - before.size),
            )
            val factors = OutcomeFactors(
                outcome = outcome,
                nights = rows.size,
                excluded = excluded,
                effects = effects.sortedWith(compareBy<FactorEffect> { it.factor.group.ordinal }.thenByDescending { it.chance }),
                waiting = waiting,
                predictive = beforePredictive,
                spread = spread,
            )
            return Analysis(factors, rows, y, follows, all, full)
        }

        private fun fit(columns: List<Column>, n: Int, y: DoubleArray, follows: BooleanArray): ShrinkageRegression? =
            ShrinkageRegression.fit(List(n) { i -> DoubleArray(columns.size) { columns[it].scaled(i) } }, y, follows)

        private fun effects(
            outcome: SleepOutcome,
            columns: List<Column>,
            fit: ShrinkageRegression,
            rows: List<Row>,
            y: DoubleArray,
            predictive: Boolean,
        ): List<FactorEffect> = columns.mapIndexed { j, column ->
            val mean = fit.mean(j)
            val chance = maxOf(1 - fit.cdf(j, outcome.minimum), fit.cdf(j, -outcome.minimum))
            val slope = mean / column.step
            val anchor = column.center + fit.centers[j] * column.step
            FactorEffect(
                factor = column.factor,
                step = column.step,
                mean = mean,
                low = fit.quantile(j, 0.1),
                high = fit.quantile(j, 0.9),
                chance = chance,
                clear = predictive && chance >= CLEAR_FROM,
                nights = column.raw.count { it != null },
                yes = if (column.factor.binary) column.raw.count { it == 1.0 } else null,
                points = rows.indices.mapNotNull { i ->
                    column.raw[i]?.let { value ->
                        FactorPoint(rows[i].night.date, value, fit.intercept + slope * (value - anchor) + (y[i] - fit.fitted[i]))
                    }
                },
                slope = slope,
                anchor = anchor,
                anchorValue = fit.intercept,
            )
        }

        /** The factor's column, or null without enough values or variety. */
        private fun column(factor: SleepFactor, rows: List<Row>): Column? {
            val raw = rows.map { it.values[factor] }
            val known = raw.filterNotNull().sorted()
            if (known.size < MIN_COVERAGE * rows.size) return null
            if (factor.binary) {
                val yes = known.count { it == 1.0 }
                if (yes < MIN_EACH_SIDE || known.size - yes < MIN_EACH_SIDE) return null
                return Column(factor, center = 0.0, step = 1.0, raw = raw)
            }
            val spread = NightlyRange.quantile(known, 0.75) - NightlyRange.quantile(known, 0.25)
            val step = niceStep(spread)
            if (step <= 0) return null
            return Column(factor, center = NightlyRange.quantile(known, 0.5), step = step, raw = raw)
        }

        /** [value] rounded to a step that reads well: fives from 20, whole numbers from 5, halves from 1, tenths below. */
        internal fun niceStep(value: Double): Double = when {
            value >= 20 -> round(value / 5) * 5
            value >= 5 -> round(value)
            value >= 1 -> round(value * 2) / 2
            else -> round(value * 10) / 10
        }

        private fun values(
            night: Night,
            all: List<Night>,
            needs: Map<LocalDate, Long>,
            bouts: List<TrainingBout>,
            form: Map<LocalDate, Double>,
            freeDays: Set<DayOfWeek>,
            strain: Map<LocalDate, Double>,
        ): Map<SleepFactor, Double?> {
            val start = night.start
            val day = bouts.filter { it.end < start && it.end >= start.minus(DAY) }
            val previous = all.lastOrNull { it.date == night.date.minusDays(1) }
            return mapOf(
                SleepFactor.TRAINING_LOAD to day.sumOf { it.stressScore },
                SleepFactor.LATE_TRAINING to if (day.any { it.end >= start.minus(LATE) && it.stressScore >= LATE_STRESS }) 1.0 else 0.0,
                SleepFactor.FORM to form[night.date.minusDays(1)],
                SleepFactor.BEDTIME to minutesAfterEvening(start, night.zone),
                SleepFactor.PREVIOUS_NIGHT to previous?.let { p -> needs[p.date]?.let { (p.asleepMillis - it) / MINUTE } },
                SleepFactor.FREE_DAY to if (night.date.dayOfWeek in freeDays) 1.0 else 0.0,
                SleepFactor.DAYTIME_STRAIN to strain[night.date.minusDays(1)],
                SleepFactor.TIME_ASLEEP to needs[night.date]?.let { (night.asleepMillis - it) / MINUTE },
                SleepFactor.AWAKE to night.continuity?.let { it.wakeAfterOnsetMillis / MINUTE },
                SleepFactor.DEEP_SLEEP to stageMinutes(night, SleepStageType.DEEP),
                SleepFactor.REM_SLEEP to stageMinutes(night, SleepStageType.REM),
                SleepFactor.DEEP_BEFORE_WAKING to deepBeforeWaking(night),
                SleepFactor.HRV to night.hrvMs?.let { hrv ->
                    baseline(all, night.date, HRV_DAYS) { it.hrvMs }?.let { 100 * ln(hrv / it) }
                },
                SleepFactor.SLEEPING_HR to night.sleepingHeartRate?.let { hr ->
                    baseline(all, night.date, HEART_RATE_DAYS) { it.sleepingHeartRate?.average }?.let { hr.average - it }
                },
                SleepFactor.SKIN_TEMPERATURE to night.skinTemperature,
            )
        }

        private fun climateValues(climate: NightClimate?): Map<SleepFactor, Double?> = mapOf(
            SleepFactor.BEDROOM_TEMPERATURE to climate?.temperature,
            SleepFactor.BEDROOM_HUMIDITY to climate?.humidity,
            SleepFactor.BEDROOM_CO2 to climate?.co2,
        )

        /** Minutes in [type]; null for a night recorded without stages, or with plain "asleep" (the watch's classic mode). */
        private fun stageMinutes(night: Night, type: SleepStageType): Double? {
            if (night.stages.none { it.type in STAGED }) return null
            return (night.stageMillis[type] ?: 0L) / MINUTE
        }

        private val STAGED = setOf(SleepStageType.LIGHT, SleepStageType.DEEP, SleepStageType.REM)

        private fun deepBeforeWaking(night: Night): Double? {
            if (night.stages.isEmpty()) return null
            val from = night.end.minus(DEEP_BEFORE_WAKING)
            return if (night.stages.any { it.type == SleepStageType.DEEP && it.end > from }) 1.0 else 0.0
        }

        /** Minutes after 18:00 where the night was spent, so that falling asleep either side of midnight compares. */
        private fun minutesAfterEvening(time: Instant, zone: ZoneId): Double {
            val minute = time.atZone(zone).toLocalTime().toSecondOfDay() / 60
            return ((minute - EVENING_MINUTE + MINUTES_PER_DAY) % MINUTES_PER_DAY).toDouble()
        }

        /** The median of [value] over the nights in the [days] before [date]; null with fewer than [BASELINE_NIGHTS]. */
        private fun baseline(all: List<Night>, date: LocalDate, days: Long, value: (Night) -> Double?): Double? {
            val from = date.minusDays(days)
            val values = all.filter { it.date < date && it.date >= from }.mapNotNull(value).sorted()
            return if (values.size < BASELINE_NIGHTS) null else NightlyRange.quantile(values, 0.5)
        }

        private const val MINUTE = 60_000.0
    }
}
