package app.pacinglab.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pacinglab.core.analytics.load.BanisterWeighting
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.model.ZoneBoundaries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.Period
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
 * @property zoneBoundaries zone boundaries the athlete set; empty to work them out from threshold heart rate.
 * @property birthDate the athlete's, for comparisons with people of the same age; null when not given.
 * @property morningReminder a notification asks the morning question when it is still open after waking.
 * @property sessionWeather the weather at the start of each session is looked up when a session opens.
 * @property weeksInFiveZones Trends' weekly table splits time over the five zones rather than the three.
 */
data class UserPreferences(
    val sessionMergeGap: Duration = TrainingSessions.DEFAULT_MAX_GAP,
    val theme: ThemeChoice = ThemeChoice.INDIGO,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val speedByDefault: Boolean = false,
    val sleepNeed: Duration? = null,
    val strengthInLoad: Boolean = true,
    val sex: Sex = Sex.MALE,
    val zoneBoundaries: ZoneBoundaries = ZoneBoundaries(),
    val birthDate: LocalDate? = null,
    val morningReminder: Boolean = false,
    val sessionWeather: Boolean = true,
    val weeksInFiveZones: Boolean = true,
) {
    /** Age in whole years on [date], or null without a birth date. */
    fun ageOn(date: LocalDate): Int? = birthDate?.let { Period.between(it, date).years }
}

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
            zoneBoundaries = ZoneBoundaries(
                five = values[FIVE_ZONES]?.let { boundaries(it, 4) },
                polarized = values[POLARIZED_ZONES]?.let { boundaries(it, 2) },
            ),
            birthDate = values[BIRTH_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            morningReminder = values[MORNING_REMINDER] ?: false,
            sessionWeather = values[SESSION_WEATHER] ?: true,
            weeksInFiveZones = values[WEEKS_IN_FIVE_ZONES] ?: true,
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

    /** Sets or, with nulls, clears the athlete's own zone boundaries. */
    suspend fun setZoneBoundaries(boundaries: ZoneBoundaries) {
        dataStore.edit {
            boundaries.five?.let { five -> it[FIVE_ZONES] = five.joinToString(",") } ?: it.remove(FIVE_ZONES)
            boundaries.polarized?.let { polarized -> it[POLARIZED_ZONES] = polarized.joinToString(",") } ?: it.remove(POLARIZED_ZONES)
        }
    }

    /** Stored boundaries read back; null when they no longer make a valid set. */
    private fun boundaries(stored: String, count: Int): List<Int>? = stored.split(",").mapNotNull { it.toIntOrNull() }
        .takeIf { it.size == count && it.zipWithNext().all { (a, b) -> b > a } }

    suspend fun setSex(sex: Sex) {
        dataStore.edit { it[SEX] = sex.name }
    }

    /** Sets or, with null, clears the athlete's birth date. */
    suspend fun setBirthDate(date: LocalDate?) {
        dataStore.edit { if (date == null) it.remove(BIRTH_DATE) else it[BIRTH_DATE] = date.toString() }
    }

    suspend fun setMorningReminder(on: Boolean) {
        dataStore.edit { it[MORNING_REMINDER] = on }
    }

    suspend fun setSessionWeather(on: Boolean) {
        dataStore.edit { it[SESSION_WEATHER] = on }
    }

    suspend fun setWeeksInFiveZones(five: Boolean) {
        dataStore.edit { it[WEEKS_IN_FIVE_ZONES] = five }
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
        private val FIVE_ZONES = stringPreferencesKey("zones_five")
        private val POLARIZED_ZONES = stringPreferencesKey("zones_polarized")
        private val BIRTH_DATE = stringPreferencesKey("birth_date")
        private val MORNING_REMINDER = booleanPreferencesKey("morning_reminder")
        private val SESSION_WEATHER = booleanPreferencesKey("session_weather")
        private val WEEKS_IN_FIVE_ZONES = booleanPreferencesKey("trends_weeks_five_zones")
    }
}
