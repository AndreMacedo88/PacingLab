package app.pacinglab.core.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.data.sync.AppVisibility
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The morning question as a notification, for an athlete who asked for it: once a morning, as soon as the
 * question opens ([MorningQuestion]: last night has synced, or the fallback time has come), while it is still
 * unanswered, before [LATEST] and only when the app is not on screen, where Today asks anyway. Runs after every
 * sync and from the morning checks ([MorningCheck]), which look for last night every few minutes.
 */
@Singleton
class MorningReminder @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val nights: NightRepository,
    private val wellness: WellnessRepository,
    private val visibility: AppVisibility,
    private val dataStore: DataStore<Preferences>,
    private val notifier: QuestionNotifier,
    private val clock: Clock,
) : AfterSync {
    override suspend fun run() {
        val settings = preferences.preferences.first()
        if (!settings.morningReminder || visibility.isForeground || isDone()) return
        val today = LocalDate.now(clock)
        val nightIn = nights.observeNights().first().any { it.date == today }
        if (!MorningQuestion.isOpen(LocalTime.now(clock), nightIn, settings.morningFallback)) return
        notifier.askRested(today)
        dataStore.edit { it[REMINDED] = today.toString() }
    }

    /** True once there is nothing left to remind of today: reminded already, answered, or too late. */
    suspend fun isDone(): Boolean {
        val today = LocalDate.now(clock)
        return LocalTime.now(clock) >= LATEST ||
            dataStore.data.first()[REMINDED] == today.toString() ||
            wellness.observeRested().first()[today] != null
    }

    companion object {
        /** No reminder from this time on: by the afternoon the question has lost its point. */
        val LATEST: LocalTime = LocalTime.of(13, 0)

        private val REMINDED = stringPreferencesKey("morning_reminder_last")

        /** The morning this installation last reminded; not the athlete's to back up. */
        internal val DEVICE_KEYS: Set<String> = setOf(REMINDED.name)
    }
}
