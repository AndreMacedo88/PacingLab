package app.pacinglab.core.analytics.load

import java.time.LocalDate

/**
 * One day of training for the calendar.
 *
 * @property intensity the day's character from the heart rate of its runs and rides: the highest
 *   intensity domain with a meaningful share of the time ([TrainingCalendar.HIGH_SHARE],
 *   [TrainingCalendar.THRESHOLD_SHARE]); null on days with only other training (gym, walks) or
 *   without heart rate.
 * @property workoutIds the day's workouts, the one with the most load first.
 */
data class CalendarDay(
    val date: LocalDate,
    val load: Double,
    val millis: Long,
    val intensity: IntensityDomain?,
    val workoutIds: List<String>,
)

/**
 * Training laid out by day, as a month grid shows it (Strava's training log, intervals.icu's calendar).
 * A day takes the colour of its hardest meaningful intensity, the way sessions are named by their goal
 * (Seiler 2010): an interval day counts as high intensity although most of its minutes were easy. Heart
 * rate lags at the start of reps, so short shares already count.
 */
object TrainingCalendar {
    /** Share of the day's time above VT2 from which it counts as a high-intensity day. */
    const val HIGH_SHARE = 0.15

    /** Share of the day's time between the thresholds from which it counts as a threshold day. */
    const val THRESHOLD_SHARE = 0.20

    /** Every day with a counted workout in [loads], by date. */
    fun days(loads: List<WorkoutLoad>): Map<LocalDate, CalendarDay> = loads.groupBy { it.date }.mapValues { (date, day) ->
        val millis = LongArray(IntensityDomain.entries.size)
        // Zones describe endurance work; a gym session's heart rate says little about how hard it was.
        for (load in day.filter { it.sport == null || it.sport in IntensityFocus.SPORTS }) {
            load.intensity?.millis?.forEachIndexed { i, ms -> if (i < millis.size) millis[i] += ms }
        }
        CalendarDay(
            date = date,
            load = day.sumOf { it.scores?.stressScore ?: 0.0 },
            millis = day.sumOf { it.durationMillis },
            intensity = dominant(millis),
            workoutIds = day.sortedByDescending { it.scores?.stressScore ?: 0.0 }.map { it.workoutId },
        )
    }

    /** The day's character from its time in the three domains; null without any. */
    fun dominant(millis: LongArray): IntensityDomain? {
        val total = millis.sum().takeIf { it > 0 } ?: return null
        return when {
            millis[IntensityDomain.ABOVE_VT2.ordinal].toDouble() / total >= HIGH_SHARE -> IntensityDomain.ABOVE_VT2
            millis[IntensityDomain.VT1_TO_VT2.ordinal].toDouble() / total >= THRESHOLD_SHARE -> IntensityDomain.VT1_TO_VT2
            else -> IntensityDomain.BELOW_VT1
        }
    }
}
