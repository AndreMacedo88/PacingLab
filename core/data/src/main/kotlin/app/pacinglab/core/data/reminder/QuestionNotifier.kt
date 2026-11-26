package app.pacinglab.core.data.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The questions PacingLab asks as notifications, answered with one tap on the notification itself; a seam so
 * that tests need no notification system.
 */
interface QuestionNotifier {
    /** Asks how rested the athlete feels on the morning of [morning]. */
    fun askRested(morning: LocalDate)

    /** Takes the morning question of [morning] away, once it was answered elsewhere. */
    fun closeRested(morning: LocalDate)

    /** Asks how hard a gym session felt. */
    fun askEffort(question: EffortQuestion)

    /** Takes the effort question away once a workout of its session was rated elsewhere. */
    fun closeEffort(workoutId: String)

    companion object {
        /** Asks nothing; for tests and callers that have no notifications. */
        val NONE: QuestionNotifier = object : QuestionNotifier {
            override fun askRested(morning: LocalDate) = Unit

            override fun closeRested(morning: LocalDate) = Unit

            override fun askEffort(question: EffortQuestion) = Unit

            override fun closeEffort(workoutId: String) = Unit
        }
    }
}

/** A gym session waiting for its effort rating; the answer is saved for each of [workoutIds]. */
data class EffortQuestion(val workoutIds: List<String>, val start: Instant, val zoneOffset: ZoneOffset?, val durationMillis: Long)
