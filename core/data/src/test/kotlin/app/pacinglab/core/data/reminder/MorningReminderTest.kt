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
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class MorningReminderTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val today = LocalDate.of(2026, 11, 20)
    private val measurements = MeasurementStore(database)
    private val wellness = WellnessRepository(WellnessStore(database))
    private val preferences = testPreferences()
    private val visibility = object : AppVisibility {
        override var isForeground = false
    }
    private var shown = 0

    private fun reminder(at: String) = Clock.fixed(Instant.parse("${today}T$at:00Z"), ZoneOffset.UTC).let { clock ->
        MorningReminder(
            preferences,
            NightRepository(measurements, DailyVitalsStore(database), preferences, clock),
            wellness,
            visibility,
            testDataStore(),
            { shown++ },
            clock,
        )
    }

    private suspend fun sleptLastNight() = measurements.saveSleep(
        listOf(
            SleepSession(
                "s1",
                Instant.parse("${today.minusDays(1)}T22:30:00Z"),
                Instant.parse("${today}T06:30:00Z"),
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
        assertThat(shown).isEqualTo(0)

        sleptLastNight()
        reminder.run()
        reminder.run()
        assertThat(shown).isEqualTo(1)
    }

    @Test
    fun `stays quiet when switched off, answered, on screen or after midday`() = runTest {
        sleptLastNight()
        reminder("07:00").run()
        assertThat(shown).isEqualTo(0)

        preferences.setMorningReminder(true)
        reminder("13:30").run()
        visibility.isForeground = true
        reminder("07:00").run()
        visibility.isForeground = false
        wellness.setRested(today, 4)
        reminder("07:00").run()
        assertThat(shown).isEqualTo(0)
    }
}
