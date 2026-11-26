package app.pacinglab.core.data.reminder

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.model.source.HealthDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes the morning checks; the platform's alarm, or a fake in tests. */
interface MorningCheckAlarm {
    fun set(at: Instant)

    fun cancel()

    /** True while a check is waiting to run. */
    fun isSet(): Boolean
}

/**
 * Looks for last night every few minutes on mornings while the morning reminder waits, so that the question
 * comes about when the watch app's own sleep notification does rather than with the next hourly sync. A phone
 * lying still overnight holds the hourly sync back for hours (Doze), so the checks run from an alarm Android
 * lets through while the phone is idle.
 *
 * Each check reads only the sleep records of the last [LOOK_BACK] from Health Connect, one call. When a sleep
 * ending today is there that the database does not have yet, a full sync brings it in, and the reminder,
 * which runs after every sync, asks. Otherwise the reminder is tried on its own, for the fallback time.
 */
@Singleton
class MorningCheck @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val reminder: MorningReminder,
    private val source: HealthDataSource,
    private val measurements: MeasurementStore,
    private val alarm: MorningCheckAlarm,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) : AfterSync {
    /**
     * Keeps the checks set while the reminder is on, and none when it is off; after every sync and at app start.
     * A check long overdue is set again too, in case Android dropped the alarm but kept its pending intent.
     */
    override suspend fun run() {
        if (!preferences.preferences.first().morningReminder) {
            alarm.cancel()
            return
        }
        val due = dataStore.data.first()[NEXT]?.let(Instant::ofEpochMilli)
        if (!alarm.isSet() || due == null || due < clock.instant().minus(OVERDUE)) arm(next())
    }

    /** One check, then the next one is set; [fullSync] reads everything new from the sources and runs the after-sync work. */
    suspend fun check(fullSync: suspend () -> Unit) {
        if (!preferences.preferences.first().morningReminder) {
            alarm.cancel()
            return
        }
        val now = LocalDateTime.now(clock)
        if (now.toLocalTime() >= MorningQuestion.EARLIEST && !reminder.isDone()) {
            if (nightWaiting(now.toLocalDate())) fullSync() else reminder.run()
        }
        arm(next())
    }

    private suspend fun arm(at: Instant) {
        alarm.set(at)
        dataStore.edit { it[NEXT] = at.toEpochMilli() }
    }

    /** The next check: at [MorningQuestion.EARLIEST], in [INTERVAL] while the question waits, else tomorrow morning. */
    internal suspend fun next(): Instant {
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val at = when {
            now.toLocalTime() < MorningQuestion.EARLIEST -> today.atTime(MorningQuestion.EARLIEST)
            reminder.isDone() -> today.plusDays(1).atTime(MorningQuestion.EARLIEST)
            else -> now.plus(INTERVAL)
        }
        return at.atZone(clock.zone).toInstant()
    }

    /** True when Health Connect has a sleep ending today that is not stored yet, or stored with another end. */
    private suspend fun nightWaiting(today: LocalDate): Boolean {
        val now = clock.instant()
        val from = now.minus(LOOK_BACK)
        val synced = try {
            source.readSleep(from, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PacingLab", "Morning check could not read sleep", e)
            return false
        }
        val endingToday = synced.filter { it.end.atZone(it.zoneOffset ?: clock.zone).toLocalDate() == today }
        if (endingToday.isEmpty()) return false
        val stored = measurements.sleepOverlapping(from, now).associate { it.sourceRecordId to it.end }
        return endingToday.any { stored[it.sourceRecordId] != it.end }
    }

    companion object {
        /**
         * Time between checks. Android delivers such alarms late by up to about ten minutes, so the reminder
         * comes 10 to 20 minutes after last night reaches Health Connect.
         */
        val INTERVAL: Duration = Duration.ofMinutes(10)

        /** Sleep records starting this long before now are read; a night from 22:00 is still in at 13:00. */
        val LOOK_BACK: Duration = Duration.ofHours(20)

        /** A check this late has been lost. */
        private val OVERDUE: Duration = Duration.ofMinutes(45)

        private val NEXT = longPreferencesKey("morning_check_next")

        /** When this installation's next check is due; not the athlete's to back up. */
        internal val DEVICE_KEYS: Set<String> = setOf(NEXT.name)
    }
}
