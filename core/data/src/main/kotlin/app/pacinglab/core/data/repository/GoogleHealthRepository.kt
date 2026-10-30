package app.pacinglab.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.model.source.SourceAuthorizationException
import app.pacinglab.core.model.source.VitalsSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Why the last read from the Google Health API failed. */
enum class GoogleHealthProblem {
    /** Access was withdrawn or has to be confirmed again: the athlete must sign in. */
    SIGN_IN_NEEDED,

    /** No connection, or the service answered with an error; the next sync tries again. */
    UNREACHABLE,
}

/**
 * @property days how many mornings have values from the API.
 * @property newest the newest of those mornings.
 */
data class GoogleHealthStatus(
    val connected: Boolean = false,
    val lastSync: Instant? = null,
    val problem: GoogleHealthProblem? = null,
    val days: Int = 0,
    val newest: LocalDate? = null,
)

/**
 * The Google Health API as a secondary source. Health Connect stays the source of sleep, workouts
 * and heart rate; the API adds Fitbit's own nightly HRV, SpO2 and skin temperature, which Health
 * Connect does not carry or carries only in part, and Fitbit's daily VO2max with its variance. Reads happen with every sync once the athlete has
 * connected, from a few days before the newest stored morning (late values arrive after the watch
 * syncs) or, the first time, from [HISTORY_DAYS] ago.
 */
@Singleton
class GoogleHealthRepository @Inject constructor(
    private val source: VitalsSource,
    private val store: DailyVitalsStore,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    val status: Flow<GoogleHealthStatus> = combine(dataStore.data, store.observe()) { prefs, vitals ->
        GoogleHealthStatus(
            connected = prefs[CONNECTED] == true,
            lastSync = prefs[LAST_SYNC]?.let(Instant::ofEpochMilli),
            problem = prefs[PROBLEM]?.let { name -> GoogleHealthProblem.entries.firstOrNull { it.name == name } },
            days = vitals.count { it.hasNightValues },
            newest = vitals.lastOrNull { it.hasNightValues }?.date,
        )
    }

    /** Marks the API as connected, once the athlete granted access, and reads it. */
    suspend fun connect() {
        dataStore.edit {
            it[CONNECTED] = true
            it.remove(PROBLEM)
        }
        sync()
    }

    /** Stops reading the API and forgets what it gave; nights fall back to Health Connect alone. */
    suspend fun disconnect() = mutex.withLock {
        dataStore.edit {
            it.remove(CONNECTED)
            it.remove(PROBLEM)
            it.remove(LAST_SYNC)
        }
        store.clear()
    }

    /** Reads new values when connected; failures are recorded in [status] and returned, never thrown. */
    suspend fun sync(): GoogleHealthProblem? = mutex.withLock {
        if (dataStore.data.first()[CONNECTED] != true) return@withLock null
        val today = LocalDate.now(clock)
        val since = store.latest()?.minusDays(REREAD_DAYS)?.coerceAtLeast(today.minusDays(HISTORY_DAYS)) ?: today.minusDays(HISTORY_DAYS)
        val vo2maxSince = store.latestVo2Max()?.minusDays(REREAD_DAYS) ?: today.minusDays(VO2MAX_HISTORY_DAYS)
        // Brief awakenings came later than the other values: read their history once, then like the rest.
        val sleepSince = store.latestBriefAwakenings()?.minusDays(REREAD_DAYS)?.coerceAtLeast(today.minusDays(HISTORY_DAYS))
            ?: today.minusDays(HISTORY_DAYS)
        val problem = try {
            store.merge(source.readDailyVitals(since, vo2maxSince, sleepSince))
            null
        } catch (_: SourceAuthorizationException) {
            GoogleHealthProblem.SIGN_IN_NEEDED
        } catch (_: IOException) {
            GoogleHealthProblem.UNREACHABLE
        } catch (_: JSONException) {
            GoogleHealthProblem.UNREACHABLE
        }
        dataStore.edit {
            if (problem == null) {
                it[LAST_SYNC] = clock.millis()
                it.remove(PROBLEM)
            } else {
                it[PROBLEM] = problem.name
            }
        }
        problem
    }

    companion object {
        /** How far back the first read goes; older nights have no Health Connect sleep to join. */
        const val HISTORY_DAYS = 120L

        /**
         * How far back VO2max is read the first time: its daily series holds every run measurement Fitbit
         * made, which the trend uses however old.
         */
        const val VO2MAX_HISTORY_DAYS = 3 * 365L

        /** Mornings read again each time, for values the watch app finishes later. */
        const val REREAD_DAYS = 3L

        private val CONNECTED = booleanPreferencesKey("google_health_connected")
        private val LAST_SYNC = longPreferencesKey("google_health_last_sync")
        private val PROBLEM = stringPreferencesKey("google_health_problem")
    }
}
