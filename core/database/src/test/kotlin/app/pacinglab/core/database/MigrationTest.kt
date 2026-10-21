package app.pacinglab.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Workout
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Builds a database exactly as an older app version created it, from the exported schema, and checks
 * that the current version opens it without losing data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `version 1 workouts survive the upgrade with route_pending false`() = runTest {
        val file = databaseFile()
        createFromSchema(file, version = 1)
        insertWorkout(file, extraColumns = "", extraValues = "")

        val workout = openAndRead(file)

        assertThat(workout.summary.averageHr).isEqualTo(150)
        assertThat(workout.routePending).isFalse()
        assertThat(workout.recordingMethod).isEqualTo(RecordingMethod.UNKNOWN)
    }

    @Test
    fun `version 2 workouts survive the upgrade with an unknown recording method`() = runTest {
        val file = databaseFile()
        createFromSchema(file, version = 2)
        insertWorkout(file, extraColumns = ", route_pending", extraValues = ", 1")

        val workout = openAndRead(file)

        assertThat(workout.summary.distanceMeters).isEqualTo(250.0)
        assertThat(workout.routePending).isTrue()
        assertThat(workout.recordingMethod).isEqualTo(RecordingMethod.UNKNOWN)
    }

    @Test
    fun `version 3 databases gain an empty physiology table`() = runTest {
        val file = databaseFile()
        createFromSchema(file, version = 3)
        insertWorkout(file, extraColumns = ", route_pending, recording_method", extraValues = ", 0, 'ACTIVE'")

        val workout = openAndRead(file)
        val database = Room.databaseBuilder(context, PacingLabDatabase::class.java, file.path).setDriver(AndroidSQLiteDriver()).build()
        val history = PhysiologyStore(database).observe().first()
        database.close()

        assertThat(workout.recordingMethod).isEqualTo(RecordingMethod.ACTIVE)
        assertThat(history.isEmpty()).isTrue()
    }

    private fun databaseFile(): File = context.getDatabasePath("migration-test.db").apply {
        parentFile?.mkdirs()
        delete()
    }

    private fun insertWorkout(file: File, extraColumns: String, extraValues: String) {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                """
                INSERT INTO workouts (id, source, source_record_id, origin, device, activity, title, start_ms, end_ms,
                    zone_offset_s, last_modified_ms, sport, duration_ms, distance_m, avg_hr, max_hr, has_route, has_speed,
                    laps, segments$extraColumns)
                VALUES ('hc:1', 'HEALTH_CONNECT', '1', 'com.fitbit.FitbitMobile', 'Sense', 'running', 'Run', 1000, 61000,
                    7200, 1000, 'RUN', 60000, 250.0, 150, 170, 1, 1, X'0100', X'0100'$extraValues)
                """.trimIndent(),
            )
        }
    }

    private suspend fun openAndRead(file: File): Workout {
        val database = Room.databaseBuilder(context, PacingLabDatabase::class.java, file.path)
            .setDriver(AndroidSQLiteDriver())
            .build()
        val workout = WorkoutStore(database).get("hc:1")
        database.close()
        assertThat(workout).isNotNull()
        return workout!!
    }

    private fun createFromSchema(file: File, version: Int) {
        val schema = JSONObject(File("schemas/${PacingLabDatabase::class.qualifiedName}/$version.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema.getString("identityHash")}')",
            )
            db.version = version
        }
    }
}
