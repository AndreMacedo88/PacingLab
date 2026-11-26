package app.pacinglab.core.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.data.repository.EffortRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.data.sync.AppVisibility
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The effort question of a gym session as a notification, for an athlete who asked for it: after the sync that
 * brings the session in, once a session, for the one Today would ask about (the newest of today or yesterday
 * without a rating), and only when the app is not on screen, where Today asks anyway.
 */
@Singleton
class GymEffortReminder @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val workouts: WorkoutRepository,
    private val efforts: EffortRepository,
    private val visibility: AppVisibility,
    private val dataStore: DataStore<Preferences>,
    private val notifier: QuestionNotifier,
    private val clock: Clock,
) : AfterSync {
    override suspend fun run() {
        if (!preferences.preferences.first().gymReminder || visibility.isForeground) return
        val session =
            EffortRepository.toRate(workouts.observeSessions().first(), efforts.observe().first(), LocalDate.now(clock), clock.zone)
                ?: return
        if (dataStore.data.first()[ASKED] == session.id) return
        notifier.askEffort(
            EffortQuestion(
                workoutIds = session.workouts.map { it.id },
                start = session.start,
                zoneOffset = session.workouts.first().zoneOffset,
                durationMillis = session.end.toEpochMilli() - session.start.toEpochMilli(),
            ),
        )
        dataStore.edit { it[ASKED] = session.id }
    }

    companion object {
        private val ASKED = stringPreferencesKey("gym_reminder_last")

        /** The session this installation last asked about; not the athlete's to back up. */
        internal val DEVICE_KEYS: Set<String> = setOf(ASKED.name)
    }
}
