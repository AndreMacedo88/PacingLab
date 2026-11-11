package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.model.RangeStanding
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
 * Nightly HRV as HRV-guided training studies use it (Plews 2013, Javaloyes 2019): the average of
 * ln(RMSSD) over the last seven nights against a normal range of the baseline mean plus or minus half a
 * standard deviation. The baseline is the 60 days before those seven, so that the nights being judged are
 * not part of what they are judged against. The average leans towards the latest nights
 * ([Readiness.recentHrv]), so that good nights after a poor spell show as recovery at once instead of after
 * the poor nights have left the week. Values are given back in milliseconds.
 *
 * @property baselineNights nights in the baseline, the recent seven days not included.
 *
 * @property recentMs the weighted average of the last seven nights.
 */
data class HrvStatus(
    val lastNightMs: Double?,
    val recentMs: Double,
    val normalLowMs: Double,
    val normalHighMs: Double,
    val standing: Standing,
    val baselineNights: Int,
    val score: Int,
)

/**
 * Last night's resting heart rate against the nights before. [usual] is their average; [normalLow] to
 * [normalHigh] is the range a single night usually falls in, the same [NightlyRange] the Today tile and
 * the Sleep screen show, or the usual level plus or minus one standard deviation (at least 3 bpm) while
 * there are too few nights for that range.
 */
data class RestingHrStatus(
    val lastNight: Double,
    val usual: Double,
    val standing: Standing,
    val score: Int,
    val normalLow: Double = usual,
    val normalHigh: Double = usual,
)

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
data class FormStatus(val form: Double, val fitness: Double, val band: Double, val standing: Standing, val score: Int)

/**
 * How ready the body is today, from what the watch measured overnight and from recent training.
 *
 * @property score 0 to 100, null until the HRV baseline has [Readiness.PROVISIONAL_NIGHTS] nights.
 * @property baselineNights nights of HRV the baseline has so far, the recent seven days not included; from
 *   [Readiness.BASELINE_NIGHTS] on the score is settled, before that it is [provisional].
 * @property hrvNights nights of HRV so far, the recent seven days included: the progress towards a first
 *   score, which comes at about [Readiness.FIRST_SCORE_NIGHTS].
 * @property breathingElevated breathing rate during sleep clearly above usual, a common early sign of illness.
 * @property breathingUsual the average breathing rate of the nights before; null with fewer than a week of them.
 */
data class Readiness(
    val date: LocalDate,
    val score: Int?,
    val level: ReadinessLevel?,
    val hrv: HrvStatus?,
    val restingHr: RestingHrStatus?,
    val sleep: SleepStatus?,
    val training: FormStatus?,
    val breathingElevated: Boolean,
    val baselineNights: Int,
    val breathingUsual: Double? = null,
    val hrvNights: Int = baselineNights,
) {
    /** True while the score rests on fewer than [BASELINE_NIGHTS] nights of HRV: a first impression. */
    val provisional: Boolean get() = score != null && baselineNights < BASELINE_NIGHTS

    companion object {
        /** Nights of HRV for a baseline that can be trusted. */
        const val BASELINE_NIGHTS = 14

        /**
         * Nights of HRV in the baseline for a first, provisional score: a week of nights before the recent week,
         * enough to place the recent average against the range seen so far, not yet to trust that range.
         */
        const val PROVISIONAL_NIGHTS = 7

        /** Days the HRV baseline looks back from the start of the recent week. */
        const val HRV_WINDOW_DAYS = 60

        /** Days of nights in the recent HRV average ([recentHrv]). */
        const val RECENT_DAYS = 7

        /** Nights of HRV, worn every night, by which the first score appears: the recent week and a week of baseline. */
        const val FIRST_SCORE_NIGHTS = PROVISIONAL_NIGHTS + RECENT_DAYS

        /**
         * Points the HRV part moves for each standard deviation the recent average lies from the baseline.
         * A lasting change of one standard deviation moves it as far as the former sum of a 7-day term and a
         * last-night term did.
         */
        const val HRV_POINTS_PER_SD = 30.0

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
            val upToToday = nights.filter { !it.date.isAfter(today) }.sortedBy { it.date }
            val recent = currentStretch(upToToday)
            val lastNight = recent.lastOrNull()?.takeIf { it.date == today }
            // The recent week is judged against the 60 days before it, never against itself.
            val weekStart = today.minusDays(RECENT_DAYS - 1L)
            val withHrv = recent.filter { it.hrvMs != null }
            val recentHrvNights = withHrv.filter { !it.date.isBefore(weekStart) }
            val baseline = withHrv.filter {
                it.date.isBefore(weekStart) && !it.date.isBefore(weekStart.minusDays(HRV_WINDOW_DAYS.toLong()))
            }
            val shortWindow = recent.filter { it.date > today.minusDays(SHORT_WINDOW_DAYS.toLong()) }

            val hrv = hrvStatus(baseline, recentHrvNights, lastNight, today)
            val restingHr = restingHrStatus(upToToday, shortWindow, lastNight)
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
                // Each part already pulls the score by its weight, low HRV included. Breathing rate has no weight
                // of its own: raised together with low HRV or a raised resting heart rate it hints at an illness
                // starting, the one case where the score is held down whatever the other parts say.
                val unwell = breathing && (it.standing == Standing.BELOW || restingHr?.standing == Standing.ABOVE)
                (if (unwell) minOf(weighted, ILLNESS_CAP) else weighted).roundToInt()
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
                baselineNights = baseline.size,
                breathingUsual = breathingUsual(shortWindow, today),
                hrvNights = baseline.size + recentHrvNights.size,
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

        private fun hrvStatus(baseline: List<Night>, recentNights: List<Night>, lastNight: Night?, today: LocalDate): HrvStatus? {
            if (baseline.size < PROVISIONAL_NIGHTS) return null
            val logs = baseline.map { ln(it.hrvMs!!) }
            val mean = logs.average()
            val sd = sqrt(logs.sumOf { (it - mean) * (it - mean) } / (logs.size - 1)).coerceAtLeast(MIN_LN_SD)
            val recent = recentLog(recentNights, today) ?: return null
            // Distance of the recent average from the baseline in standard deviations; half of one is the
            // smallest worthwhile change, the edge of the normal range.
            val position = (recent - mean) / sd
            return HrvStatus(
                lastNightMs = lastNight?.hrvMs,
                recentMs = exp(recent),
                normalLowMs = exp(mean - sd / 2),
                normalHighMs = exp(mean + sd / 2),
                standing = standing(position, -SMALLEST_WORTHWHILE_SD, SMALLEST_WORTHWHILE_SD),
                baselineNights = baseline.size,
                score = clampScore(70 + HRV_POINTS_PER_SD * position),
            )
        }

        /**
         * Nightly HRV of the [RECENT_DAYS] days up to [date], averaged on the log scale with the latest nights
         * counting most: [date] seven times, the day before six times, down to once for six days before. A
         * missing night drops out with its weight. Null without any HRV in those days.
         */
        fun recentHrv(nights: List<Night>, date: LocalDate): Double? = recentLog(nights, date)?.let(::exp)

        private fun recentLog(nights: List<Night>, date: LocalDate): Double? {
            var sum = 0.0
            var weights = 0.0
            for (night in nights) {
                val hrv = night.hrvMs?.takeIf { it > 0 } ?: continue
                val back = ChronoUnit.DAYS.between(night.date, date)
                if (back < 0 || back >= RECENT_DAYS) continue
                val weight = (RECENT_DAYS - back).toDouble()
                sum += weight * ln(hrv)
                weights += weight
            }
            return if (weights == 0.0) null else sum / weights
        }

        /**
         * [all] are every night up to today, for the same single-night range the Today tile shows; [nights] the
         * current stretch's last 30 days, for the usual level and its spread.
         */
        private fun restingHrStatus(all: List<Night>, nights: List<Night>, lastNight: Night?): RestingHrStatus? {
            val tonight = lastNight?.restingHr ?: return null
            val values = nights.filter { it.date < lastNight.date }.mapNotNull { it.restingHr }
            if (values.size < MIN_SHORT_BASELINE) return null
            val usual = values.average()
            val sd = sqrt(values.sumOf { (it - usual) * (it - usual) } / (values.size - 1)).coerceAtLeast(1.0)
            val position = (tonight - usual) / sd
            val range = NightlyRange.of(all, NightlyMetric.RESTING_HR, lastNight.date)
            val margin = maxOf(sd, RESTING_HR_MARGIN)
            val standing = when (range?.standing(tonight)) {
                RangeStanding.ABOVE -> Standing.ABOVE

                RangeStanding.BELOW -> Standing.BELOW

                RangeStanding.IN_RANGE -> Standing.NORMAL

                null -> when {
                    position > 1 && tonight - usual >= RESTING_HR_MARGIN -> Standing.ABOVE
                    position < -1 && usual - tonight >= RESTING_HR_MARGIN -> Standing.BELOW
                    else -> Standing.NORMAL
                }
            }
            return RestingHrStatus(
                lastNight = tonight,
                usual = usual,
                standing = standing,
                score = clampScore(70 - 15 * position),
                normalLow = range?.low ?: (usual - margin),
                normalHigh = range?.high ?: (usual + margin),
            )
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

        /** Half-width of the band of normal form at [fitness]: 30 % of fitness, at least 10. */
        fun formBand(fitness: Double): Double = maxOf(MIN_FORM_BAND, FORM_BAND_SHARE * fitness)

        private fun trainingStatus(day: FormDay): FormStatus {
            val band = formBand(day.fitness)
            val position = day.form / band
            return FormStatus(day.form, day.fitness, band, standing(position, -1.0, 1.0), clampScore(75 + 25 * position))
        }

        private fun breathingUsual(nights: List<Night>, today: LocalDate): Double? =
            nights.filter { it.date < today }.mapNotNull { it.respiratoryRate }.takeIf { it.size >= MIN_SHORT_BASELINE }?.average()

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

        /** The smallest worthwhile change in HRV, in standard deviations: the edge of its normal range. */
        private const val SMALLEST_WORTHWHILE_SD = 0.5

        /** Breathing this much above usual, in breaths a minute, counts as raised. */
        const val BREATHING_MARGIN = 1.0
        private const val MIN_FORM_BAND = 10.0
        private const val FORM_BAND_SHARE = 0.3

        /** The most the score can be while breathing is raised together with low HRV or a raised resting heart rate. */
        const val ILLNESS_CAP = 40.0

        /** Scores from here up are [ReadinessLevel.READY]. */
        const val READY_FROM = 70

        /** Scores from here up to [READY_FROM] are [ReadinessLevel.MODERATE], below it [ReadinessLevel.LOW]. */
        const val MODERATE_FROM = 50
    }
}
