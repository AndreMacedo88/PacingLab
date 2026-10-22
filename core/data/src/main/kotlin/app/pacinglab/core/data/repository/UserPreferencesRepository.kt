package app.pacinglab.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.analytics.load.BanisterWeighting
import app.pacinglab.core.analytics.workout.TrainingSessions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** The accent colours the app wears. */
enum class ThemeChoice {
    INDIGO,
    AMBER,
    GREEN,
    BLUE,
}

/** Sets the heart rate weighting of training load: Banister fitted TRIMP to blood lactate separately by sex. */
enum class Sex(val weighting: BanisterWeighting) {
    MALE(BanisterWeighting.MALE),
    FEMALE(BanisterWeighting.FEMALE),
}

enum class DarkMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * App preferences that are not physiology (those have effective dates and live elsewhere).
 *
 * @property sessionMergeGap workouts closer together than this, with the same sport, form one training session.
 * @property speedByDefault running speed shows as km/h rather than pace when a session opens.
 * @property sleepNeed a fixed sleep need; null lets PacingLab work it out from the athlete's nights.
 * @property strengthInLoad gym sessions count towards training load, fitness and fatigue.
 * @property sex the athlete's, for the weighting of training load.
 */
data class UserPreferences(
    val sessionMergeGap: Duration = TrainingSessions.DEFAULT_MAX_GAP,
    val theme: ThemeChoice = ThemeChoice.INDIGO,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val speedByDefault: Boolean = false,
    val sleepNeed: Duration? = null,
    val strengthInLoad: Boolean = true,
    val sex: Sex = Sex.MALE,
)

@Singleton
class UserPreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { values ->
        UserPreferences(
            sessionMergeGap = values[SESSION_MERGE_GAP_MINUTES]?.let { Duration.ofMinutes(it.toLong()) }
                ?: TrainingSessions.DEFAULT_MAX_GAP,
            theme = values[THEME]?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.INDIGO,
            darkMode = values[DARK_MODE]?.let { name -> DarkMode.entries.firstOrNull { it.name == name } } ?: DarkMode.SYSTEM,
            speedByDefault = values[SPEED_BY_DEFAULT] ?: false,
            sleepNeed = values[SLEEP_NEED_MINUTES]?.let { Duration.ofMinutes(it.toLong()) },
            strengthInLoad = values[STRENGTH_IN_LOAD] ?: true,
            sex = values[SEX]?.let { name -> Sex.entries.firstOrNull { it.name == name } } ?: Sex.MALE,
        )
    }

    suspend fun setSessionMergeGap(gap: Duration) {
        require(!gap.isNegative && gap <= MAX_MERGE_GAP) { "Merge gap must be between 0 and $MAX_MERGE_GAP" }
        dataStore.edit { it[SESSION_MERGE_GAP_MINUTES] = gap.toMinutes().toInt() }
    }

    suspend fun setTheme(theme: ThemeChoice) {
        dataStore.edit { it[THEME] = theme.name }
    }

    suspend fun setDarkMode(mode: DarkMode) {
        dataStore.edit { it[DARK_MODE] = mode.name }
    }

    suspend fun setSpeedByDefault(speed: Boolean) {
        dataStore.edit { it[SPEED_BY_DEFAULT] = speed }
    }

    /** A fixed sleep need, or null to work it out from the nights. */
    suspend fun setSleepNeed(need: Duration?) {
        require(need == null || need in MIN_SLEEP_NEED..MAX_SLEEP_NEED) { "Sleep need must be between $MIN_SLEEP_NEED and $MAX_SLEEP_NEED" }
        dataStore.edit { if (need == null) it.remove(SLEEP_NEED_MINUTES) else it[SLEEP_NEED_MINUTES] = need.toMinutes().toInt() }
    }

    suspend fun setStrengthInLoad(count: Boolean) {
        dataStore.edit { it[STRENGTH_IN_LOAD] = count }
    }

    suspend fun setSex(sex: Sex) {
        dataStore.edit { it[SEX] = sex.name }
    }

    companion object {
        val MAX_MERGE_GAP: Duration = Duration.ofMinutes(60)
        val MIN_SLEEP_NEED: Duration = Duration.ofHours(6)
        val MAX_SLEEP_NEED: Duration = Duration.ofHours(10)
        private val SESSION_MERGE_GAP_MINUTES = intPreferencesKey("session_merge_gap_minutes")
        private val THEME = stringPreferencesKey("theme")
        private val DARK_MODE = stringPreferencesKey("dark_mode")
        private val SPEED_BY_DEFAULT = booleanPreferencesKey("speed_by_default")
        private val SLEEP_NEED_MINUTES = intPreferencesKey("sleep_need_minutes")
        private val STRENGTH_IN_LOAD = booleanPreferencesKey("strength_in_load")
        private val SEX = stringPreferencesKey("sex")
    }
}
