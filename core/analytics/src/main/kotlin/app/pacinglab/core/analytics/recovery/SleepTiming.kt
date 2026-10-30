package app.pacinglab.core.analytics.recovery

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a chronotype falls: rough bands around the averages that large surveys report. */
enum class Chronotype { EARLY, INTERMEDIATE, LATE }

/** How far sleep on free days drifts from sleep on workdays (Roenneberg 2012). */
enum class SocialJetlagLevel { LOW, MODERATE, HIGH }

/**
 * When the athlete sleeps on workdays and on free days.
 *
 * @property work the nights before workdays; [free] the nights before free days.
 * @property chronotype mid-sleep on free days corrected for the sleep debt built up on workdays (MSFsc),
 *   in minutes after midnight; null until both kinds of night are known ([ready]).
 * @property socialJetlagMinutes the gap between mid-sleep on free days and on workdays; null likewise.
 */
data class SleepTiming(val work: SleepWindow?, val free: SleepWindow?, val chronotype: Int?, val socialJetlagMinutes: Int?) {
    val ready: Boolean get() = chronotype != null

    val chronotypeKind: Chronotype?
        get() = chronotype?.let { clock ->
            // Mid-sleep before midnight is earlier still, not later.
            val minutes = if (clock >= 12 * 60) clock - 24 * 60 else clock
            when {
                minutes < EARLY_BEFORE -> Chronotype.EARLY
                minutes < LATE_FROM -> Chronotype.INTERMEDIATE
                else -> Chronotype.LATE
            }
        }

    val socialJetlag: SocialJetlagLevel?
        get() = socialJetlagMinutes?.let {
            when {
                it < MODERATE_FROM -> SocialJetlagLevel.LOW
                it < HIGH_FROM -> SocialJetlagLevel.MODERATE
                else -> SocialJetlagLevel.HIGH
            }
        }

    companion object {
        /** Nights looked at: four weeks, enough for a few weekends without reaching back to another season. */
        const val DAYS = 28

        /** Fewest nights of each kind before the measures are given. */
        const val MIN_FREE_NIGHTS = 2
        const val MIN_WORK_NIGHTS = 3

        /** Chronotype bands, in minutes after midnight of mid-sleep on free days. */
        const val EARLY_BEFORE = 3 * 60
        const val LATE_FROM = 5 * 60

        /** Social jetlag bands: about two thirds of people have an hour or more, a third two hours (Roenneberg 2012). */
        const val MODERATE_FROM = 60
        const val HIGH_FROM = 120

        /**
         * The timing of the [DAYS] nights up to [until]. A night counts as free when the morning after it is
         * one of [freeDays] (sleep before a day off can run late), as the Munich ChronoType Questionnaire
         * defines it. Null without any night in the window.
         */
        fun of(nights: List<Night>, until: LocalDate, freeDays: Set<DayOfWeek>): SleepTiming? {
            val recent = nights.filter { it.date <= until && it.date > until.minusDays(DAYS.toLong()) }
            if (recent.isEmpty()) return null
            val (freeNights, workNights) = recent.partition { it.date.dayOfWeek in freeDays }
            val work = SleepWindow.of(workNights)
            val free = SleepWindow.of(freeNights)
            if (work == null || free == null || work.nights < MIN_WORK_NIGHTS || free.nights < MIN_FREE_NIGHTS) {
                return SleepTiming(work, free, null, null)
            }
            // Sleep on free days partly pays back the workdays' debt; the correction takes that out (Roenneberg 2004).
            val freePerWeek = freeDays.size.coerceIn(1, 6)
            val weekAverage = (work.durationMinutes * (7 - freePerWeek) + free.durationMinutes * freePerWeek) / 7.0
            val corrected = if (free.durationMinutes > work.durationMinutes) {
                free.midpoint - (free.durationMinutes - weekAverage) / 2
            } else {
                free.midpoint.toDouble()
            }
            return SleepTiming(
                work = work,
                free = free,
                chronotype = SleepWindow.clock(corrected.roundToInt()),
                socialJetlagMinutes = abs(free.midpoint - work.midpoint),
            )
        }
    }
}

/**
 * The average night of a group: falling asleep, waking for good and the middle of the two, as minutes
 * counted from noon so that nights do not wrap at midnight; [durationMinutes] is the time between falling
 * asleep and waking (the questionnaire's sleep duration, awakenings included).
 */
data class SleepWindow(val nights: Int, val onset: Int, val wake: Int) {
    val midpoint: Int get() = (onset + wake) / 2
    val durationMinutes: Int get() = wake - onset

    /** [onset], [midpoint] and [wake] as minutes after midnight. */
    val onsetClock: Int get() = clock(onset)
    val midpointClock: Int get() = clock(midpoint)
    val wakeClock: Int get() = clock(wake)

    companion object {
        private const val NOON = 12 * 60
        private const val DAY = 24 * 60

        fun of(nights: List<Night>): SleepWindow? {
            if (nights.isEmpty()) return null
            val onsets = nights.map { fromNoon(it.continuity?.onset ?: it.start, it) }
            val wakes = nights.map { night ->
                val onset = fromNoon(night.continuity?.onset ?: night.start, night)
                val asleep = Duration.between(night.continuity?.onset ?: night.start, night.continuity?.finalAwakening ?: night.end)
                onset + asleep.toMinutes().toInt()
            }
            return SleepWindow(nights.size, onsets.average().roundToInt(), wakes.average().roundToInt())
        }

        /** Minutes from noon back to minutes after midnight. */
        fun clock(fromNoon: Int): Int = ((fromNoon + NOON) % DAY + DAY) % DAY

        private fun fromNoon(t: Instant, night: Night): Int = (t.atZone(night.zone).toLocalTime().toSecondOfDay() / 60 - NOON + DAY) % DAY
    }
}
