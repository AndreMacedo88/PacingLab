package app.pacinglab.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.pacinglab.core.database.store.EffortStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WellnessStore
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.PhysiologyEntry
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** How many items of each kind a backup held or a restore brought back. */
data class BackupSummary(val physiology: Int, val tests: Int, val mornings: Int, val efforts: Int, val settings: Int) {
    /** Entries, settings not counted. */
    val entries: Int get() = physiology + tests + mornings + efforts
}

/** Why a file could not be restored. */
class BackupFormatException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION, UNREADABLE_VALUE }
}

/**
 * Everything the athlete entered by hand, which Health Connect cannot give back: heart rate values with
 * their dates, VO2max tests, morning ratings, gym effort ratings and every setting, dashboards and zone
 * boundaries included. Workouts, nights and measurements are left out; a sync brings them back.
 *
 * The file is plain JSON, so it also moves data between the debug and release builds. Restoring
 * merges: an entry in the backup replaces the one of the same day or workout, other entries stay, and
 * every setting in the backup takes its value from it.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val physiology: PhysiologyStore,
    private val measurements: MeasurementStore,
    private val wellness: WellnessStore,
    private val efforts: EffortStore,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    /** The backup as JSON text, with what it holds. */
    suspend fun export(): Pair<String, BackupSummary> {
        val entries = physiology.observe().first().entries
        val tests = measurements.observeMeasurements(MeasurementKind.VO2_MAX, Instant.EPOCH, FAR_FUTURE).first()
            .filter { it.origin == PerformanceRepository.TEST_ORIGIN }
        val rested = wellness.observeRested().first()
        val rated = efforts.observe().first()
        val settings = dataStore.data.first().asMap()

        val json = JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("created", clock.instant().toString())
            .put("physiology", JSONArray(entries.map { it.toJson() }))
            .put("vo2maxTests", JSONArray(tests.map { it.toJson() }))
            .put("mornings", JSONArray(rested.map { (date, rating) -> JSONObject().put("date", date.toString()).put("rested", rating) }))
            .put("efforts", JSONArray(rated.map { (workout, effort) -> JSONObject().put("workout", workout).put("effort", effort) }))
            .put("settings", JSONArray(settings.map { (key, value) -> setting(key.name, value) }))
        return json.toString(2) to BackupSummary(entries.size, tests.size, rested.size, rated.size, settings.size)
    }

    /** Restores the backup in [text]; nothing is changed when it cannot be read. */
    suspend fun restore(text: String): BackupSummary {
        val backup = try {
            parse(text)
        } catch (e: JSONException) {
            throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP, e)
        } catch (e: RuntimeException) {
            throw BackupFormatException(BackupFormatException.Reason.UNREADABLE_VALUE, e)
        }
        backup.physiology.forEach { physiology.save(it) }
        measurements.saveMeasurements(backup.tests)
        backup.mornings.forEach { (date, rating) -> wellness.setRested(date, rating) }
        backup.efforts.forEach { (workout, effort) -> efforts.set(workout, effort) }
        dataStore.edit { prefs -> backup.settings.forEach { it(prefs) } }
        return BackupSummary(backup.physiology.size, backup.tests.size, backup.mornings.size, backup.efforts.size, backup.settings.size)
    }

    private class Backup(
        val physiology: List<PhysiologyEntry>,
        val tests: List<Measurement>,
        val mornings: Map<LocalDate, Int>,
        val efforts: Map<String, Int>,
        val settings: List<(MutablePreferences) -> Unit>,
    )

    /** Reads everything first, so that a broken file changes nothing. */
    private fun parse(text: String): Backup {
        val json = JSONObject(text)
        val format = json.optInt("format")
        if (json.optString("app") != APP || format < 1) throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP)
        if (format > FORMAT) throw BackupFormatException(BackupFormatException.Reason.NEWER_VERSION)
        return Backup(
            physiology = json.optJSONArray("physiology").objects().map {
                PhysiologyEntry(
                    LocalDate.parse(it.getString("from")),
                    it.intOrNull("maxHr"),
                    it.intOrNull("thresholdHr"),
                    it.intOrNull("restingHr"),
                )
            },
            tests = json.optJSONArray("vo2maxTests").objects().map {
                Measurement(
                    sourceRecordId = it.getString("id"),
                    kind = MeasurementKind.VO2_MAX,
                    time = Instant.parse(it.getString("time")),
                    zoneOffset = it.optString("offset").takeIf(String::isNotEmpty)?.let(ZoneOffset::of),
                    value = it.getDouble("value"),
                    origin = PerformanceRepository.TEST_ORIGIN,
                )
            },
            mornings = json.optJSONArray("mornings").objects().associate { LocalDate.parse(it.getString("date")) to it.getInt("rested") },
            efforts = json.optJSONArray("efforts").objects().associate { it.getString("workout") to it.getInt("effort") },
            settings = json.optJSONArray("settings").objects().map(::settingWriter),
        )
    }

    private fun PhysiologyEntry.toJson() = JSONObject()
        .put("from", effectiveFrom.toString())
        .putOpt("maxHr", maxHr)
        .putOpt("thresholdHr", thresholdHr)
        .putOpt("restingHr", restingHr)

    private fun Measurement.toJson() = JSONObject()
        .put("id", sourceRecordId)
        .put("time", time.toString())
        .putOpt("offset", zoneOffset?.id)
        .put("value", value)

    private fun setting(name: String, value: Any): JSONObject {
        val (type, encoded) = when (value) {
            is Boolean -> "boolean" to value
            is Int -> "int" to value
            is Long -> "long" to value
            is Float -> "float" to value.toDouble()
            is Double -> "double" to value
            is Set<*> -> "strings" to JSONArray(value.map { it.toString() })
            else -> "string" to value.toString()
        }
        return JSONObject().put("key", name).put("type", type).put("value", encoded)
    }

    private fun settingWriter(setting: JSONObject): (MutablePreferences) -> Unit {
        val name = setting.getString("key")
        return when (val type = setting.getString("type")) {
            "boolean" -> setting.getBoolean("value").let { value -> { it[booleanPreferencesKey(name)] = value } }

            "int" -> setting.getInt("value").let { value -> { it[intPreferencesKey(name)] = value } }

            "long" -> setting.getLong("value").let { value -> { it[longPreferencesKey(name)] = value } }

            "float" -> setting.getDouble("value").toFloat().let { value -> { it[floatPreferencesKey(name)] = value } }

            "double" -> setting.getDouble("value").let { value -> { it[doublePreferencesKey(name)] = value } }

            "strings" -> setting.getJSONArray("value").let { array ->
                val value = (0 until array.length()).map(array::getString).toSet()
                return { it[stringSetPreferencesKey(name)] = value }
            }

            "string" -> setting.getString("value").let { value -> { it[stringPreferencesKey(name)] = value } }

            else -> throw BackupFormatException(
                BackupFormatException.Reason.UNREADABLE_VALUE,
                IllegalArgumentException("Setting type $type"),
            )
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map(::getJSONObject)

    private fun JSONObject.intOrNull(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null

    companion object {
        const val APP = "PacingLab"
        const val FORMAT = 1
        const val MIME_TYPE = "application/json"
        private val FAR_FUTURE = Instant.parse("2200-01-01T00:00:00Z")

        /** The suggested file name for a backup made on [date]. */
        fun fileName(date: LocalDate): String = "pacinglab-backup-$date.json"
    }
}
