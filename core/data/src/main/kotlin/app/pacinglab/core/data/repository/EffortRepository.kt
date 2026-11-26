package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.data.reminder.QuestionNotifier
import app.pacinglab.core.database.store.EffortStore
import app.pacinglab.core.model.Sport
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How hard the athlete rated workouts (session RPE, 1 to 10), by workout id; used for gym sessions. A rating given
 * in the app takes the session's notification away.
 */
@Singleton
class EffortRepository @Inject constructor(private val store: EffortStore, private val notifier: QuestionNotifier = QuestionNotifier.NONE) {
    fun observe(): Flow<Map<String, Int>> = store.observe()

    /** Saves the rating; [fromNotification] leaves the notification it came from to show that it was saved. */
    suspend fun rate(workoutId: String, effort: Int, fromNotification: Boolean = false) {
        store.set(workoutId, effort)
        if (!fromNotification) notifier.closeEffort(workoutId)
    }

    suspend fun clear(workoutId: String) = store.clear(workoutId)

    companion object {
        /**
         * The newest gym session, when it is from [today] or the day before and none of its workouts is rated:
         * the one the app asks about. [sessions] are newest first.
         */
        fun toRate(sessions: List<TrainingSession>, rated: Map<String, Int>, today: LocalDate, zone: ZoneId): TrainingSession? {
            val session = sessions.firstOrNull { it.sport == Sport.STRENGTH } ?: return null
            val date = TrainingLoads.dateOf(session.workouts.first(), zone)
            return session.takeUnless { date < today.minusDays(1) || it.workouts.any { workout -> workout.id in rated } }
        }
    }
}
