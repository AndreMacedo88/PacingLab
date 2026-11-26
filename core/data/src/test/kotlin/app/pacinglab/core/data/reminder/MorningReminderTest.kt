package app.pacinglab.core.data.reminder

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.testDataStore
import app.pacinglab.core.data.testPreferences
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.WellnessStore
import app.pacinglab.core.model.SleepSession
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class MorningReminderTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val today = LocalDate.of(2026, 11, 20)
    private val measurements = MeasurementStore(database)
    private val notifier = RecordingNotifier()
    private val wellness = WellnessRepository(WellnessStore(database), notifier)
    private val preferences = testPreferences()
    private val dataStore = testDataStore()
    private val visibility = object : AppVisibility {
        override var isForeground = false
    }

    private fun reminder(at: String) = Clock.fixed(Instant.parse("${today}T$at:00Z"), ZoneOffset.UTC).let { clock ->
        MorningReminder(
            preferences,
            NightRepository(measurements, DailyVitalsStore(database), preferences, clock),
            wellness,
            visibility,
            dataStore,
            notifier,
            clock,
        )
    }

    private suspend fun slept(until: String = "06:30") = measurements.saveSleep(
        listOf(
            SleepSession(
                "s1",
                Instant.parse("${today.minusDays(1)}T22:30:00Z"),
                Instant.parse("${today}T$until:00Z"),
                ZoneOffset.UTC,
                "watch",
                emptyList(),
            ),
        ),
    )

    @After
    fun tearDown() = database.close()

    @Test
    fun `asks once a morning after the night has synced, while the question is open`() = runTest {
        preferences.setMorningReminder(true)
        val reminder = reminder("07:00")

        reminder.run()
        assertThat(notifier.rested).isEmpty()

        slept()
        reminder.run()
        reminder.run()
        assertThat(notifier.rested).containsExactly(today)
    }

    @Test
    fun `without last night asks from the fallback time`() = runTest {
        preferences.setMorningReminder(true)
        preferences.setMorningFallback(LocalTime.of(9, 30))

        reminder("09:29").run()
        assertThat(notifier.rested).isEmpty()

        reminder("09:30").run()
        assertThat(notifier.rested).containsExactly(today)
    }

    @Test
    fun `a night that ends before five waits for five`() = runTest {
        preferences.setMorningReminder(true)
        slept(until = "03:40")

        reminder("04:00").run()
        assertThat(notifier.rested).isEmpty()

        reminder("05:00").run()
        assertThat(notifier.rested).containsExactly(today)
    }

    @Test
    fun `stays quiet when switched off, answered, on screen or after midday`() = runTest {
        slept()
        reminder("07:00").run()
        assertThat(notifier.rested).isEmpty()

        preferences.setMorningReminder(true)
        reminder("13:30").run()
        visibility.isForeground = true
        reminder("07:00").run()
        visibility.isForeground = false
        wellness.setRested(today, 4)
        reminder("07:00").run()
        assertThat(notifier.rested).isEmpty()
    }

    @Test
    fun `an answer given in the app takes the notification away, one from the notification leaves it`() = runTest {
        wellness.setRested(today, 4, fromNotification = true)
        assertThat(notifier.closed).isEmpty()

        wellness.setRested(today, 3)
        assertThat(notifier.closed).containsExactly(today.toString())
    }
}
