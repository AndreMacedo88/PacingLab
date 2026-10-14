package app.pacinglab.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import app.pacinglab.core.analytics.workout.TrainingSessions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** App preferences that are not physiology (those have effective dates and live elsewhere). */
data class UserPreferences(
    /** Workouts closer together than this, with the same sport, form one training session. */
    val sessionMergeGap: Duration = TrainingSessions.DEFAULT_MAX_GAP,
)

@Singleton
class UserPreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { values ->
        UserPreferences(
            sessionMergeGap = values[SESSION_MERGE_GAP_MINUTES]?.let { Duration.ofMinutes(it.toLong()) }
                ?: TrainingSessions.DEFAULT_MAX_GAP,
        )
    }

    suspend fun setSessionMergeGap(gap: Duration) {
        require(!gap.isNegative && gap <= MAX_MERGE_GAP) { "Merge gap must be between 0 and $MAX_MERGE_GAP" }
        dataStore.edit { it[SESSION_MERGE_GAP_MINUTES] = gap.toMinutes().toInt() }
    }

    companion object {
        val MAX_MERGE_GAP: Duration = Duration.ofMinutes(60)
        private val SESSION_MERGE_GAP_MINUTES = intPreferencesKey("session_merge_gap_minutes")
    }
}
