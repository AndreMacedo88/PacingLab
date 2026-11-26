package app.pacinglab.core.data.reminder

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.data.sync.FakeHealthDataSource
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
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
class MorningCheckTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val today = LocalDate.of(2026, 11, 26)
    private val measurements = MeasurementStore(database)
    private val source = FakeHealthDataSource()
    private val notifier = RecordingNotifier()
    private val preferences = testPreferences()
    private val dataStore = testDataStore()
    private val alarm = FakeAlarm()
    private var fullSyncs = 0
    private val night = SleepSession(
        "night",
        Instant.parse("${today.minusDays(1)}T23:35:00Z"),
        Instant.parse("${today}T05:53:00Z"),
        ZoneOffset.UTC,
        "watch",
        emptyList(),
    )

    private fun clock(at: String) = Clock.fixed(Instant.parse("${today}T$at:00Z"), ZoneOffset.UTC)

    private fun reminder(at: String) = MorningReminder(
        preferences,
        NightRepository(measurements, DailyVitalsStore(database), preferences, clock(at)),
        WellnessRepository(WellnessStore(database)),
        object : AppVisibility {
            override val isForeground = false
        },
        dataStore,
        notifier,
        clock(at),
    )

    private fun check(at: String) = MorningCheck(preferences, reminder(at), source, measurements, alarm, dataStore, clock(at))

    /** One check at [at]; its full sync stores what Health Connect has, then the after-sync reminder runs. */
    private suspend fun checkAt(at: String) = check(at).check {
        fullSyncs++
        measurements.saveSleep(source.sleep.values.toList())
        reminder(at).run()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a new night in Health Connect brings a full sync, after which the reminder asks`() = runTest {
        preferences.setMorningReminder(true)

        checkAt("06:20")
        assertThat(fullSyncs).isEqualTo(0)
        assertThat(alarm.at).isEqualTo(Instant.parse("${today}T06:30:00Z"))

        source.addSleep(night)
        checkAt("06:30")
        assertThat(fullSyncs).isEqualTo(1)
        assertThat(notifier.rested).containsExactly(today)
        // Asked: nothing more to do until tomorrow morning.
        assertThat(alarm.at).isEqualTo(Instant.parse("${today.plusDays(1)}T05:00:00Z"))
    }

    @Test
    fun `a night already stored needs no sync, and the fallback time asks without one`() = runTest {
        preferences.setMorningReminder(true)
        source.addSleep(night)
        measurements.saveSleep(listOf(night))

        checkAt("06:30")
        assertThat(fullSyncs).isEqualTo(0)
        assertThat(notifier.rested).containsExactly(today)
    }

    @Test
    fun `before five nothing is read and the next check is at five`() = runTest {
        preferences.setMorningReminder(true)
        source.addSleep(night)

        checkAt("03:10")
        assertThat(fullSyncs).isEqualTo(0)
        assertThat(alarm.at).isEqualTo(Instant.parse("${today}T05:00:00Z"))
    }

    @Test
    fun `the checks stop when switched off and are set once when switched on`() = runTest {
        check("06:30").run()
        assertThat(alarm.at).isNull()

        preferences.setMorningReminder(true)
        check("06:30").run()
        assertThat(alarm.at).isEqualTo(Instant.parse("${today}T06:40:00Z"))
        check("06:35").run()
        assertThat(alarm.at).isEqualTo(Instant.parse("${today}T06:40:00Z"))
        // Set but long overdue: lost, so set again.
        check("07:30").run()
        assertThat(alarm.at).isEqualTo(Instant.parse("${today}T07:40:00Z"))

        preferences.setMorningReminder(false)
        checkAt("06:40")
        assertThat(alarm.at).isNull()
        assertThat(notifier.rested).isEmpty()
    }

    private class FakeAlarm : MorningCheckAlarm {
        var at: Instant? = null

        override fun set(at: Instant) {
            this.at = at
        }

        override fun cancel() {
            at = null
        }

        override fun isSet() = at != null
    }
}
