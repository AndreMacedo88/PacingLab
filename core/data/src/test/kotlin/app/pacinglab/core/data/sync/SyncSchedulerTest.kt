package app.pacinglab.core.data.sync

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.data.repository.GoogleHealthRepository
import app.pacinglab.core.data.testDataStore
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.SyncStateStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.DailyVitals
import app.pacinglab.core.model.source.ChangeChannel
import app.pacinglab.core.model.source.VitalsSource
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class SyncSchedulerTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-29T07:00:00Z"), ZoneOffset.UTC)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, PacingLabDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private val source = FakeHealthDataSource()
    private val visibility = object : AppVisibility {
        override var isForeground = true
    }
    private val sync = HealthSync(source, WorkoutStore(database), MeasurementStore(database), SyncStateStore(database), visibility, clock)
    private val noVitals = object : VitalsSource {
        override suspend fun readDailyVitals(since: LocalDate, vo2maxSince: LocalDate, sleepSince: LocalDate): List<DailyVitals> =
            emptyList()
    }
    private val googleHealth = GoogleHealthRepository(noVitals, DailyVitalsStore(database), testDataStore(), clock)

    @After
    fun tearDown() = database.close()

    private fun TestScope.scheduler() = SyncScheduler(context, backgroundScope, sync, googleHealth, visibility, emptySet())

    @Test
    fun `a pull keeps the indicator turning for at least a second`() = runTest {
        val scheduler = scheduler()
        val start = testScheduler.currentTime

        scheduler.refresh()
        assertThat(scheduler.refreshing.first()).isTrue()
        scheduler.refreshing.first { !it }

        assertThat(testScheduler.currentTime - start).isGreaterThanOrEqualTo(SyncScheduler.MIN_REFRESH_MILLIS)
    }

    @Test
    fun `a pull that cannot read workouts says so, and one that works says nothing`() = runTest {
        val scheduler = scheduler()
        val problems = mutableListOf<SyncProblem>()
        backgroundScope.launch { scheduler.problems.collect { problems += it } }
        runCurrent()

        scheduler.refresh()
        scheduler.refreshing.first { !it }
        runCurrent()
        assertThat(problems).isEmpty()

        source.channels = setOf(ChangeChannel.HEART_RATE)
        scheduler.refresh()
        scheduler.refreshing.first { !it }
        runCurrent()
        assertThat(problems).containsExactly(SyncProblem.NO_ACCESS)
    }
}
