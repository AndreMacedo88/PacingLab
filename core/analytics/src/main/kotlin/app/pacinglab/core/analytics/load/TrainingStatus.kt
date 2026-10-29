package app.pacinglab.core.analytics.load

import java.time.LocalDate

/** Training status in the words Garmin made familiar; each has a fixed rule in [TrainingStatus.of]. */
enum class TrainingStatusKind {
    /** Load in the usual range and VO2max rising: training is working. */
    PRODUCTIVE,

    /** Load in the usual range and VO2max steady. */
    MAINTAINING,

    /** A lighter week after training at a high level, VO2max holding: fresh for a race. */
    PEAKING,

    /** A lighter week: the body catches up. */
    RECOVERY,

    /** Load in the usual range but VO2max falling: recovery may not be keeping up. */
    UNPRODUCTIVE,

    /** Much less training than usual for a while, and fitness falling. */
    DETRAINING,

    /** Load far above the usual: more than the body can absorb for long. */
    OVERREACHING,

    /** HRV below its normal range while training goes on: a sign of strain. */
    STRAINED,
}

/** How VO2max moved over the last four weeks. */
enum class Vo2MaxDirection {
    RISING,
    STEADY,
    FALLING,
}

/**
 * The training status of a day with the evidence behind it.
 *
 * @property loadRatio fatigue (7-day load) over fitness (42-day load): the exponentially weighted
 *   acute:chronic workload ratio.
 * @property vo2maxChange change of the VO2max trend over [TrainingStatus.VO2MAX_DAYS] days; null without a trend.
 * @property fitnessChange change of fitness over [TrainingStatus.FITNESS_DAYS] days.
 * @property hrvBelowNormal whether the week's HRV sits below its normal range; null without a baseline.
 */
data class TrainingStatus(
    val date: LocalDate,
    val kind: TrainingStatusKind,
    val loadRatio: Double,
    val fitness: Double,
    val fitnessChange: Double,
    val vo2maxChange: Double?,
    val hrvBelowNormal: Boolean?,
) {
    val vo2maxDirection: Vo2MaxDirection?
        get() = vo2maxChange?.let {
            when {
                it >= VO2MAX_STEP -> Vo2MaxDirection.RISING
                it <= -VO2MAX_STEP -> Vo2MaxDirection.FALLING
                else -> Vo2MaxDirection.STEADY
            }
        }

    companion object {
        /** Load ratio below which a week counts as light, and from which load is very high (Gabbett 2016). */
        const val LIGHT_BELOW = 0.8
        const val HIGH_ABOVE = 1.5

        /** Load ratio below which training has nearly stopped. */
        const val DETRAINING_BELOW = 0.5

        /** VO2max change over four weeks that counts as a change; the trend's own scatter is about this size. */
        const val VO2MAX_STEP = 0.5
        const val VO2MAX_DAYS = 28L
        const val FITNESS_DAYS = 14L

        /** Fitness below this (a stress score of 10 a day) is too little training to describe. */
        const val MIN_FITNESS = 10.0

        /** Days of load the fitness model needs before its values mean anything. */
        const val MIN_DAYS = 28

        /** Share of its 90-day high that fitness must hold for a light week to count as peaking. */
        private const val PEAK_FITNESS_SHARE = 0.9
        private const val PEAK_DAYS = 90L

        /**
         * The status on the last day of [series] (the fitness model up to the day), from three things:
         * the load ratio, the VO2max trend's change over four weeks ([vo2maxChange]) and HRV against its
         * normal range ([hrvBelowNormal], as readiness judges it).
         * - HRV below normal while training: [TrainingStatusKind.STRAINED].
         * - Ratio above 1.5: [TrainingStatusKind.OVERREACHING].
         * - Ratio below 0.5 and fitness falling over two weeks: [TrainingStatusKind.DETRAINING].
         * - Ratio below 0.8: [TrainingStatusKind.PEAKING] when form is positive, fitness holds 90 % of its
         *   90-day high and VO2max is not falling; else [TrainingStatusKind.RECOVERY].
         * - Otherwise by VO2max: rising [TrainingStatusKind.PRODUCTIVE], falling
         *   [TrainingStatusKind.UNPRODUCTIVE], steady or unknown [TrainingStatusKind.MAINTAINING].
         *
         * Null with fewer than [MIN_DAYS] days or fitness under [MIN_FITNESS].
         */
        fun of(series: List<FormDay>, vo2maxChange: Double?, hrvBelowNormal: Boolean?): TrainingStatus? {
            if (series.size < MIN_DAYS) return null
            val today = series.last()
            if (today.fitness < MIN_FITNESS) return null
            val ratio = today.fatigue / today.fitness
            val fitnessChange = today.fitness - series[series.size - 1 - FITNESS_DAYS.toInt()].fitness
            val recentHigh = series.takeLast(PEAK_DAYS.toInt()).maxOf { it.fitness }
            val vo2Falling = vo2maxChange != null && vo2maxChange <= -VO2MAX_STEP
            val kind = when {
                hrvBelowNormal == true && ratio >= DETRAINING_BELOW -> TrainingStatusKind.STRAINED

                ratio > HIGH_ABOVE -> TrainingStatusKind.OVERREACHING

                ratio < DETRAINING_BELOW && fitnessChange < 0 -> TrainingStatusKind.DETRAINING

                ratio < LIGHT_BELOW ->
                    if (today.form > 0 && today.fitness >= PEAK_FITNESS_SHARE * recentHigh && !vo2Falling) {
                        TrainingStatusKind.PEAKING
                    } else {
                        TrainingStatusKind.RECOVERY
                    }

                vo2maxChange == null -> TrainingStatusKind.MAINTAINING

                vo2maxChange >= VO2MAX_STEP -> TrainingStatusKind.PRODUCTIVE

                vo2Falling -> TrainingStatusKind.UNPRODUCTIVE

                else -> TrainingStatusKind.MAINTAINING
            }
            return TrainingStatus(today.date, kind, ratio, today.fitness, fitnessChange, vo2maxChange, hrvBelowNormal)
        }
    }
}
