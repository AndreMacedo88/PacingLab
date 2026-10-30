package app.pacinglab.core.analytics.load

import app.pacinglab.core.model.SessionKind
import java.time.LocalDate

/**
 * One day of training for the calendar.
 *
 * @property intensity the day's hardest session goal: a named run's from its kind
 *   ([TrainingCalendar.domainOf]), other runs and rides from their heart rate (the highest domain with a
 *   meaningful share of the time, [TrainingCalendar.HIGH_SHARE], [TrainingCalendar.THRESHOLD_SHARE]);
 *   null on days with only other training (gym, walks) or without heart rate.
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
 * A day takes the colour of its hardest session, named by its goal (Seiler 2010's session-goal view): an
 * interval day counts as hard although most of its minutes were easy. Runs the session page names keep
 * that name's domain, so that a threshold session whose heart rate crossed VT2 still reads as threshold
 * work; the others are judged by heart rate, where short shares already count (it lags at rep starts).
 */
object TrainingCalendar {
    /** Share of the day's time above VT2 from which it counts as a high-intensity day. */
    const val HIGH_SHARE = 0.15

    /** Share of the day's time between the thresholds from which it counts as a threshold day. */
    const val THRESHOLD_SHARE = 0.20

    /** Every day with a counted workout in [loads], by date; [kinds] names running sessions by workout id. */
    fun days(loads: List<WorkoutLoad>, kinds: Map<String, SessionKind> = emptyMap()): Map<LocalDate, CalendarDay> =
        loads.groupBy { it.date }.mapValues { (date, day) ->
            val millis = LongArray(IntensityDomain.entries.size)
            val goals = mutableListOf<IntensityDomain>()
            for (load in day) {
                val goal = kinds[load.workoutId]?.let(::domainOf)
                when {
                    goal != null -> goals += goal

                    // Zones describe endurance work; a gym session's heart rate says little about how hard it was.
                    load.sport == null || load.sport in IntensityFocus.SPORTS ->
                        load.intensity?.millis?.forEachIndexed { i, ms -> if (i < millis.size) millis[i] += ms }
                }
            }
            CalendarDay(
                date = date,
                load = day.sumOf { it.scores?.stressScore ?: 0.0 },
                millis = day.sumOf { it.durationMillis },
                intensity = (goals + listOfNotNull(dominant(millis))).maxOrNull(),
                workoutIds = day.sortedByDescending { it.scores?.stressScore ?: 0.0 }.map { it.workoutId },
            )
        }

    /**
     * The domain a session of [kind] aims at, as Seiler's three zones file it: tempo and threshold work
     * between the thresholds, VO2max intervals and sprints above; null for kinds that do not say (plain
     * runs, reps whose heart rate could not follow).
     */
    fun domainOf(kind: SessionKind): IntensityDomain? = when (kind) {
        SessionKind.SPRINTS, SessionKind.VO2MAX_INTERVALS -> IntensityDomain.ABOVE_VT2

        SessionKind.THRESHOLD_INTERVALS, SessionKind.TEMPO_INTERVALS, SessionKind.THRESHOLD_RUN, SessionKind.TEMPO_RUN ->
            IntensityDomain.VT1_TO_VT2

        SessionKind.EASY_RUN, SessionKind.LONG_RUN -> IntensityDomain.BELOW_VT1

        SessionKind.INTERVALS, SessionKind.RUN -> null
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
