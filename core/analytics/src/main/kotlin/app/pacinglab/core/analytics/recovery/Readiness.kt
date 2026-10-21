package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.analytics.load.FormDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where a value sits against the athlete's own normal range. */
enum class Standing {
    BELOW,
    NORMAL,
    ABOVE,
}

enum class ReadinessLevel {
    LOW,
    MODERATE,
    READY,
}

/**
 * Nightly HRV as HRV-guided training studies use it (Plews 2013, Javaloyes 2019): the 7-day average
 * of ln(RMSSD) against a normal range of the baseline mean plus or minus half a standard deviation.
 * Values are given back in milliseconds.
 */
data class HrvStatus(
    val lastNightMs: Double?,
    val weekAverageMs: Double,
    val normalLowMs: Double,
    val normalHighMs: Double,
    val standing: Standing,
    val baselineNights: Int,
    val score: Int,
)

/** Last night's resting heart rate against its usual level. */
data class RestingHrStatus(val lastNight: Double, val usual: Double, val standing: Standing, val score: Int)

/** Last night's sleep score, with the average score of the last three nights. Poor sleep (below 60) is [Standing.BELOW]. */
data class SleepStatus(
    val lastNightMillis: Long,
    val needMillis: Long,
    val lastNight: SleepScore,
    val threeNightScore: Int,
    val standing: Standing,
    val score: Int,
)

/** Form against a band that scales with fitness: below it training strain is high, above it the athlete is fresh. */
data class TrainingStatus(val form: Double, val fitness: Double, val band: Double, val standing: Standing, val score: Int)

/**
 * How ready the body is today, from what the watch measured overnight and from recent training.
 *
 * @property score 0 to 100, null while the HRV baseline is still being built.
 * @property baselineNights nights of HRV the baseline has so far; [Readiness.BASELINE_NIGHTS] are needed.
 * @property breathingElevated breathing rate during sleep clearly above usual, a common early sign of illness.
 */
data class Readiness(
    val date: LocalDate,
    val score: Int?,
    val level: ReadinessLevel?,
    val hrv: HrvStatus?,
    val restingHr: RestingHrStatus?,
    val sleep: SleepStatus?,
    val training: TrainingStatus?,
    val breathingElevated: Boolean,
    val baselineNights: Int,
) {
    companion object {
        /** Nights of HRV before a baseline means anything. */
        const val BASELINE_NIGHTS = 14

        /** Days the HRV baseline looks back. */
        const val HRV_WINDOW_DAYS = 60

        /** Days resting heart rate, sleep and breathing rate baselines look back. */
        const val SHORT_WINDOW_DAYS = 30

        /** A pause in wearing the watch at night longer than this starts the baselines afresh. */
        const val BASELINE_GAP_DAYS = 14

        /** Contributions to the score; missing contributors leave their weight to the others. */
        const val HRV_WEIGHT = 0.35
        const val SLEEP_WEIGHT = 0.25
        const val RESTING_HR_WEIGHT = 0.2
        const val TRAINING_WEIGHT = 0.2

        /**
         * Readiness for [today] from [nights] (any order, older nights are ignored) and the fitness model
         * day for [today], if there is one. [sleepScores] passes scores already computed elsewhere, so that
         * readiness and the sleep screens agree; without them they are computed here.
         */
        fun of(nights: List<Night>, form: FormDay?, today: LocalDate, sleepScores: Map<LocalDate, SleepScore>? = null): Readiness {
            val recent = currentStretch(nights.filter { !it.date.isAfter(today) }.sortedBy { it.date })
            val lastNight = recent.lastOrNull()?.takeIf { it.date == today }
            val hrvNights = recent.filter { it.hrvMs != null && it.date > today.minusDays(HRV_WINDOW_DAYS.toLong()) }
            val shortWindow = recent.filter { it.date > today.minusDays(SHORT_WINDOW_DAYS.toLong()) }

            val hrv = hrvStatus(hrvNights, lastNight, today)
            val restingHr = restingHrStatus(shortWindow, lastNight)
            val sleep = sleepStatus(shortWindow, lastNight, today, sleepScores)
            val training = form?.let(::trainingStatus)
            val breathing = breathingElevated(shortWindow, lastNight)

            val score = hrv?.let {
                val parts = listOfNotNull(
                    it.score to HRV_WEIGHT,
                    sleep?.let { s -> s.score to SLEEP_WEIGHT },
                    restingHr?.let { r -> r.score to RESTING_HR_WEIGHT },
                    training?.let { t -> t.score to TRAINING_WEIGHT },
                )
                val weighted = parts.sumOf { (s, w) -> s * w } / parts.sumOf { (_, w) -> w }
                val unwell = breathing && (it.standing == Standing.BELOW || restingHr?.standing == Standing.ABOVE)
                // HRV-guided training prescribes easy days while the week's HRV sits below the normal range,
                // whatever sleep and load say; a raised breathing rate on top hints at illness.
                val cap = when {
                    unwell -> ILLNESS_CAP
                    it.standing == Standing.BELOW -> MODERATE_FROM - 1.0
                    else -> 100.0
                }
                minOf(weighted, cap).roundToInt()
            }
            return Readiness(
                date = today,
                score = score,
                level = score?.let {
                    when {
                        it >= READY_FROM -> ReadinessLevel.READY
                        it >= MODERATE_FROM -> ReadinessLevel.MODERATE
                        else -> ReadinessLevel.LOW
                    }
                },
                hrv = hrv,
                restingHr = restingHr,
                sleep = sleep,
                training = training,
                breathingElevated = breathing,
                baselineNights = hrvNights.size,
            )
        }

        /** Nights since the last long pause in wearing the watch to bed. */
        internal fun currentStretch(nights: List<Night>): List<Night> {
            var start = 0
            for (i in 1 until nights.size) {
                if (ChronoUnit.DAYS.between(nights[i - 1].date, nights[i].date) > BASELINE_GAP_DAYS) start = i
            }
            return nights.subList(start, nights.size)
        }

        private fun hrvStatus(nights: List<Night>, lastNight: Night?, today: LocalDate): HrvStatus? {
            if (nights.size < BASELINE_NIGHTS) return null
            val logs = nights.map { ln(it.hrvMs!!) }
            val mean = logs.average()
            val sd = sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1)).coerceAtLeast(MIN_LN_SD)
            val week = nights.filter { it.date > today.minusDays(7) }.map { ln(it.hrvMs!!) }
            if (week.isEmpty()) return null
            val weekMean = week.average()
            // Position of the week's average in units of half a standard deviation, the smallest worthwhile change.
            val weekPosition = (weekMean - mean) / (sd / 2)
            val nightPosition = lastNight?.hrvMs?.let { (ln(it) - mean) / sd } ?: 0.0
            return HrvStatus(
                lastNightMs = lastNight?.hrvMs,
                weekAverageMs = exp(weekMean),
                normalLowMs = exp(mean - sd / 2),
                normalHighMs = exp(mean + sd / 2),
                standing = standing(weekPosition, -1.0, 1.0),
                baselineNights = nights.size,
                score = clampScore(70 + 10 * weekPosition + 10 * nightPosition),
            )
        }

        private fun restingHrStatus(nights: List<Night>, lastNight: Night?): RestingHrStatus? {
            val tonight = lastNight?.restingHr ?: return null
            val values = nights.mapNotNull { it.restingHr }
            if (values.size < MIN_SHORT_BASELINE) return null
            val usual = values.average()
            val sd = sqrt(values.sumOf { (it - usual) * (it - usual) } / (values.size - 1)).coerceAtLeast(1.0)
            val position = (tonight - usual) / sd
            val standing = when {
                position > 1 && tonight - usual >= RESTING_HR_MARGIN -> Standing.ABOVE
                position < -1 && usual - tonight >= RESTING_HR_MARGIN -> Standing.BELOW
                else -> Standing.NORMAL
            }
            return RestingHrStatus(tonight, usual, standing, clampScore(70 - 15 * position))
        }

        private fun sleepStatus(
            nights: List<Night>,
            lastNight: Night?,
            today: LocalDate,
            known: Map<LocalDate, SleepScore>?,
        ): SleepStatus? {
            val tonight = lastNight ?: return null
            val scores = known?.filterKeys { date -> nights.any { it.date == date } } ?: SleepScore.all(nights)
            val last = scores.getValue(tonight.date)
            val threeNights = scores.filterKeys { it > today.minusDays(3) }.values.map { it.score }
            val threeNightScore = threeNights.average().roundToInt()
            return SleepStatus(
                lastNightMillis = tonight.asleepMillis,
                needMillis = SleepScore.need(nights, tonight.date),
                lastNight = last,
                threeNightScore = threeNightScore,
                standing = if (last.rating == SleepRating.POOR) Standing.BELOW else Standing.NORMAL,
                score = clampScore(0.6 * last.score + 0.4 * threeNightScore),
            )
        }

        private fun trainingStatus(day: FormDay): TrainingStatus {
            val band = maxOf(MIN_FORM_BAND, FORM_BAND_SHARE * day.fitness)
            val position = day.form / band
            return TrainingStatus(day.form, day.fitness, band, standing(position, -1.0, 1.0), clampScore(75 + 25 * position))
        }

        private fun breathingElevated(nights: List<Night>, lastNight: Night?): Boolean {
            val tonight = lastNight?.respiratoryRate ?: return false
            val earlier = nights.filter { it.date < lastNight.date }.mapNotNull { it.respiratoryRate }
            if (earlier.size < MIN_SHORT_BASELINE) return false
            return tonight - earlier.average() >= BREATHING_MARGIN
        }

        private fun standing(position: Double, low: Double, high: Double) = when {
            position < low -> Standing.BELOW
            position > high -> Standing.ABOVE
            else -> Standing.NORMAL
        }

        private fun clampScore(value: Double): Int = value.coerceIn(0.0, 100.0).roundToInt()

        /** Nights before resting heart rate, sleep and breathing rate have a usual value. */
        const val MIN_SHORT_BASELINE = 7
        private const val MIN_LN_SD = 0.05
        private const val RESTING_HR_MARGIN = 3.0
        private const val BREATHING_MARGIN = 1.0
        private const val MIN_FORM_BAND = 10.0
        private const val FORM_BAND_SHARE = 0.3
        private const val ILLNESS_CAP = 40.0
        private const val READY_FROM = 70
        private const val MODERATE_FROM = 50
    }
}
