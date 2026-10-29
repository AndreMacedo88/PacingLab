package app.pacinglab.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.testDataStore
import app.pacinglab.core.data.testPreferences
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.EffortStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WellnessStore
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.ZoneBoundaries
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class BackupRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.ofHours(1))
    private val databases = mutableListOf<PacingLabDatabase>()

    /** One installation of the app: its own database and preferences. */
    private inner class Install {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
            .also { databases += it }
        val dataStore = testDataStore()
        val physiology = PhysiologyStore(database)
        val measurements = MeasurementStore(database)
        val wellness = WellnessStore(database)
        val efforts = EffortStore(database)
        val preferences = testPreferences(dataStore)
        val backup = BackupRepository(physiology, measurements, wellness, efforts, dataStore, clock)

        suspend fun vo2max() = measurements.observeMeasurements(MeasurementKind.VO2_MAX, Instant.EPOCH, clock.instant()).first()
    }

    private fun vo2max(id: String, value: Double, origin: String, offset: ZoneOffset?) =
        Measurement(id, MeasurementKind.VO2_MAX, Instant.parse("2026-09-01T10:00:00Z"), offset, value, origin)

    @After
    fun tearDown() = databases.forEach { it.close() }

    @Test
    fun `everything entered by hand moves to another installation`() = runTest {
        val debug = Install()
        debug.physiology.save(PhysiologyEntry(LocalDate.of(2025, 8, 14), maxHr = 186))
        debug.physiology.save(PhysiologyEntry(LocalDate.of(2026, 10, 1), maxHr = 186, thresholdHr = 167, restingHr = 48))
        debug.measurements.saveMeasurements(
            listOf(
                vo2max("pacinglab:vo2max-test:2026-09-01", 54.5, PerformanceRepository.TEST_ORIGIN, ZoneOffset.ofHours(2)),
                vo2max("fitbit-vo2", 53.0, "com.fitbit.FitbitMobile", offset = null),
            ),
        )
        debug.wellness.setRested(LocalDate.of(2026, 10, 27), 4)
        debug.efforts.set("gym-1", 7)
        debug.preferences.setTheme(ThemeChoice.GREEN)
        debug.preferences.setSleepNeed(Duration.ofMinutes(480))
        debug.preferences.setZoneBoundaries(ZoneBoundaries(five = listOf(140, 150, 160, 172)))
        DashboardRepository(debug.dataStore).save(Dashboard.TODAY, listOf(PlacedCard("hrv", true), PlacedCard("readiness", false)))

        val (text, written) = debug.backup.export()
        val release = Install()
        val read = release.backup.restore(text)

        assertThat(written).isEqualTo(BackupSummary(physiology = 2, tests = 1, mornings = 1, efforts = 1, settings = 5))
        assertThat(read).isEqualTo(written)
        assertThat(release.physiology.observe().first().entries).isEqualTo(debug.physiology.observe().first().entries)
        assertThat(release.vo2max()).containsExactly(debug.vo2max().first { it.origin == PerformanceRepository.TEST_ORIGIN })
        assertThat(release.wellness.observeRested().first()).isEqualTo(mapOf(LocalDate.of(2026, 10, 27) to 4))
        assertThat(release.efforts.observe().first()).isEqualTo(mapOf("gym-1" to 7))
        assertThat(release.preferences.preferences.first()).isEqualTo(debug.preferences.preferences.first())
        assertThat(release.dataStore.data.first().asMap().mapKeys { it.key.name })
            .isEqualTo(debug.dataStore.data.first().asMap().mapKeys { it.key.name })
    }

    @Test
    fun `restoring replaces entries of the same day and keeps the others`() = runTest {
        val old = Install()
        old.wellness.setRested(LocalDate.of(2026, 10, 20), 2)
        old.physiology.save(PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 190))
        val (text, _) = old.backup.export()

        val current = Install()
        current.wellness.setRested(LocalDate.of(2026, 10, 20), 5)
        current.wellness.setRested(LocalDate.of(2026, 10, 21), 3)
        current.physiology.save(PhysiologyEntry(LocalDate.of(2026, 6, 1), maxHr = 188))
        current.backup.restore(text)

        assertThat(current.wellness.observeRested().first())
            .isEqualTo(mapOf(LocalDate.of(2026, 10, 20) to 2, LocalDate.of(2026, 10, 21) to 3))
        assertThat(current.physiology.observe().first().entries.map { it.effectiveFrom })
            .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1))
    }

    @Test
    fun `a file that is not a backup changes nothing`() = runTest {
        val install = Install()
        install.wellness.setRested(LocalDate.of(2026, 10, 20), 2)

        suspend fun reason(text: String) = assertFailure {
            install.backup.restore(text)
        }.isInstanceOf<BackupFormatException>().prop(BackupFormatException::reason)

        reason("""{"name": "something else"}""").isEqualTo(BackupFormatException.Reason.NOT_A_BACKUP)
        reason("not json").isEqualTo(BackupFormatException.Reason.NOT_A_BACKUP)
        reason("""{"app": "PacingLab", "format": 1, "mornings": [{"date": "2026-10-21", "rested": 4}, {"date": "yesterday"}]}""")
            .isEqualTo(BackupFormatException.Reason.UNREADABLE_VALUE)
        reason("""{"app": "PacingLab", "format": 99}""").isEqualTo(BackupFormatException.Reason.NEWER_VERSION)

        assertThat(install.wellness.observeRested().first()).isEqualTo(mapOf(LocalDate.of(2026, 10, 20) to 2))
    }
}
