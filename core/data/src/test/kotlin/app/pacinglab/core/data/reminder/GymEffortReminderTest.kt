package app.pacinglab.core.data.reminder

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.repository.EffortRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.testDataStore
import app.pacinglab.core.data.testPreferences
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.EffortStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.WorkoutSummary
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class GymEffortReminderTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val workouts = WorkoutStore(database)
    private val notifier = RecordingNotifier()
    private val efforts = EffortRepository(EffortStore(database), notifier)
    private val preferences = testPreferences()
    private val visibility = object : AppVisibility {
        override var isForeground = false
    }
    private val reminder = GymEffortReminder(
        preferences,
        WorkoutRepository(workouts, preferences),
        efforts,
        visibility,
        testDataStore(),
        notifier,
        Clock.fixed(Instant.parse("2026-11-26T19:30:00Z"), ZoneOffset.UTC),
    )

    @After
    fun tearDown() = database.close()

    @Test
    fun `asks once about a new gym session that has no rating`() = runTest {
        preferences.setGymReminder(true)
        reminder.run()
        assertThat(notifier.efforts).isEmpty()

        workouts.save(workout("gym", "2026-11-26T18:05:00Z", minutes = 52, Sport.STRENGTH), WorkoutStreams.EMPTY)
        workouts.save(workout("run", "2026-11-26T07:00:00Z", minutes = 40, Sport.RUN), WorkoutStreams.EMPTY)
        reminder.run()
        reminder.run()

        assertThat(notifier.efforts.map { it.workoutIds }).containsExactly(listOf("hc:gym"))
        assertThat(notifier.efforts.single().durationMillis).isEqualTo(Duration.ofMinutes(52).toMillis())
    }

    @Test
    fun `stays quiet when switched off, on screen or rated`() = runTest {
        workouts.save(workout("gym", "2026-11-26T18:05:00Z", minutes = 52, Sport.STRENGTH), WorkoutStreams.EMPTY)
        reminder.run()

        preferences.setGymReminder(true)
        visibility.isForeground = true
        reminder.run()
        visibility.isForeground = false
        efforts.rate("hc:gym", 6)
        reminder.run()

        assertThat(notifier.efforts).isEmpty()
        // Rated in the app, so its notification goes.
        assertThat(notifier.closed).containsExactly("hc:gym")
    }

    private fun workout(id: String, start: String, minutes: Long, sport: Sport): Workout {
        val begin = Instant.parse(start)
        return Workout(
            id = "hc:$id",
            source = DataSource.HEALTH_CONNECT,
            sourceRecordId = id,
            origin = "com.fitbit.FitbitMobile",
            device = null,
            activity = sport.name.lowercase(),
            title = null,
            start = begin,
            end = begin.plus(Duration.ofMinutes(minutes)),
            zoneOffset = ZoneOffset.UTC,
            lastModified = begin,
            sport = sport,
            summary = WorkoutSummary(minutes * 60_000, null, 110, 140, hasRoute = false, hasSpeed = false),
            laps = emptyList(),
            segments = emptyList(),
            recordingMethod = RecordingMethod.ACTIVE,
        )
    }
}
