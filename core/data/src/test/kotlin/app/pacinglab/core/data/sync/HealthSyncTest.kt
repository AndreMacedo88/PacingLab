package app.pacinglab.core.data.sync

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.SyncStateStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.source.ChangeChannel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
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
class HealthSyncTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val source = FakeHealthDataSource()
    private val visibility = object : AppVisibility {
        override var isForeground = true
    }
    private lateinit var database: PacingLabDatabase
    private lateinit var workouts: WorkoutStore
    private lateinit var measurements: MeasurementStore
    private lateinit var syncState: SyncStateStore
    private lateinit var sync: HealthSync

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
        workouts = WorkoutStore(database)
        measurements = MeasurementStore(database)
        syncState = SyncStateStore(database)
        sync = HealthSync(source, workouts, measurements, syncState, visibility, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `first sync imports the whole history across several windows`() = runTest {
        source.addWorkout(workout("old", daysAgo = 400))
        source.addWorkout(workout("middle", daysAgo = 95))
        source.addWorkout(workout("recent", daysAgo = 1))

        val outcome = sync.sync()

        assertThat(outcome.importComplete).isTrue()
        assertThat(outcome.errors).isEmpty()
        assertThat(stored()).containsExactlyInAnyOrder("old", "middle", "recent")
        assertThat(source.workoutReads > 10).isTrue()
        assertThat(sync.info.first().lastSuccess).isEqualTo(now)
    }

    @Test
    fun `interval workouts with running speed are classified as runs and summarised`() = runTest {
        val start = now.minus(Duration.ofDays(2))
        val speed = SampleSeries(LongArray(600) { start.toEpochMilli() + it * 1_000L }, DoubleArray(600) { 4.0 })
        val heartRate = HeartRateSeries(LongArray(600) { start.toEpochMilli() + it * 1_000L }, IntArray(600) { 160 })
        source.addWorkout(
            workout("intervals", daysAgo = 2, activity = "boot_camp"),
            WorkoutStreams(heartRate, speed, Route.EMPTY, reportedDistanceMeters = 2_400.0),
        )

        sync.sync()

        val saved = workouts.get(HealthSync.workoutId("intervals"))
        assertThat(saved).isNotNull()
        assertThat(saved!!.sport).isEqualTo(Sport.RUN)
        assertThat(saved.summary.distanceMeters).isEqualTo(2_400.0)
        assertThat(saved.summary.averageHr).isEqualTo(160)
        assertThat(workouts.streams(saved.id).speed.size).isEqualTo(600)
    }

    @Test
    fun `an interrupted import resumes without starting over`() = runTest {
        source.addWorkout(workout("old", daysAgo = 200))
        source.addWorkout(workout("recent", daysAgo = 1))
        source.failReadsBefore = now.minus(Duration.ofDays(100))

        val first = sync.sync()

        assertThat(first.importComplete).isFalse()
        assertThat(first.errors).isNotEmpty()
        assertThat(stored()).containsExactly("recent")
        assertThat(sync.info.first().lastSuccess).isNull()

        val readsBefore = source.workoutReads
        val second = sync.sync()

        assertThat(second.importComplete).isTrue()
        assertThat(stored()).containsExactlyInAnyOrder("old", "recent")
        // Only the remaining windows (about 100 of the 200 days) are read again.
        assertThat(source.workoutReads - readsBefore <= 5).isTrue()
    }

    @Test
    fun `later syncs apply new and deleted workouts from the change log`() = runTest {
        source.addWorkout(workout("a", daysAgo = 3))
        sync.sync()

        source.addWorkout(workout("b", daysAgo = 0))
        source.deleteWorkout("a")
        val outcome = sync.sync()

        assertThat(stored()).containsExactly("b")
        assertThat(outcome.deletedWorkouts).isEqualTo(1)
    }

    @Test
    fun `heart rate written after the workout refreshes its streams`() = runTest {
        source.addWorkout(workout("a", daysAgo = 1))
        sync.sync()
        assertThat(workouts.streams(HealthSync.workoutId("a")).heartRate.isEmpty()).isTrue()

        val start = now.minus(Duration.ofDays(1))
        source.replaceStreams("a", WorkoutStreams.EMPTY.copy(heartRate = HeartRateSeries.of(start.toEpochMilli() to 150)))
        sync.sync()

        assertThat(workouts.streams(HealthSync.workoutId("a")).heartRate.size).isEqualTo(1)
    }

    @Test
    fun `an expired token re-reads the last 30 days and drops vanished workouts`() = runTest {
        source.addWorkout(workout("kept", daysAgo = 5))
        source.addWorkout(workout("vanished", daysAgo = 6))
        source.addWorkout(workout("ancient", daysAgo = 90))
        sync.sync()

        source.deleteWorkoutSilently("vanished")
        source.deleteWorkoutSilently("ancient")
        source.expireNextToken = true
        sync.sync()

        // Outside the re-read window nothing can be known, so "ancient" stays.
        assertThat(stored()).containsExactlyInAnyOrder("kept", "ancient")
    }

    @Test
    fun `measurements arrive through import and changes`() = runTest {
        source.addWorkout(workout("a", daysAgo = 10))
        source.addMeasurement(rhr("r1", daysAgo = 9, bpm = 50.0))
        sync.sync()
        source.addMeasurement(rhr("r2", daysAgo = 0, bpm = 48.0))
        sync.sync()

        val values = measurements.observeMeasurements(MeasurementKind.RESTING_HEART_RATE, now.minus(Duration.ofDays(30)), now).first()
        assertThat(values.map { it.value }).containsExactly(50.0, 48.0)
    }

    @Test
    fun `without workout access nothing is read`() = runTest {
        source.channels = setOf(ChangeChannel.HEART_RATE)
        source.addWorkout(workout("a", daysAgo = 1))

        val outcome = sync.sync()

        assertThat(outcome.noAccess).isTrue()
        assertThat(stored()).isEmpty()
        assertThat(source.workoutReads).isEqualTo(0)
    }

    @Test
    fun `status returns to idle after a run`() = runTest {
        source.addWorkout(workout("a", daysAgo = 1))

        sync.sync()

        assertThat(sync.status.value).isEqualTo(SyncStatus.Idle)
        assertThat(sync.lastOutcome.value?.savedWorkouts).isEqualTo(1)
    }

    @Test
    fun `the import waits until the app is in the foreground`() = runTest {
        source.addWorkout(workout("a", daysAgo = 3))
        visibility.isForeground = false

        val outcome = sync.sync()

        assertThat(outcome.deferred).isTrue()
        assertThat(source.workoutReads).isEqualTo(0)

        visibility.isForeground = true
        sync.sync()
        assertThat(stored()).containsExactly("a")
    }

    @Test
    fun `a rate limit pauses the import without an error and it later continues`() = runTest {
        source.addWorkout(workout("old", daysAgo = 200))
        source.addWorkout(workout("recent", daysAgo = 1))
        sync.sync()
        assertThat(stored()).containsExactlyInAnyOrder("old", "recent")

        source.addWorkout(workout("new", daysAgo = 0))
        source.rateLimitNextCall = true
        val limited = sync.sync()

        assertThat(limited.rateLimited).isTrue()
        assertThat(limited.errors).isEmpty()
        assertThat(sync.sync().rateLimited).isFalse()
        assertThat(stored()).containsExactlyInAnyOrder("old", "recent", "new")
    }

    @Test
    fun `routes withheld in the background are fetched once the app is visible`() = runTest {
        source.addWorkout(workout("a", daysAgo = 10))
        sync.sync()

        visibility.isForeground = false
        source.routesAvailable = false
        source.addWorkout(workout("run", daysAgo = 0, route = sampleRoute(daysAgo = 0)))
        sync.sync()
        val pending = workouts.get(HealthSync.workoutId("run"))!!
        assertThat(pending.routePending).isTrue()
        assertThat(pending.summary.hasRoute).isFalse()

        visibility.isForeground = true
        source.routesAvailable = true
        sync.sync()
        val fetched = workouts.get(HealthSync.workoutId("run"))!!
        assertThat(fetched.routePending).isFalse()
        assertThat(workouts.streams(fetched.id).route.size).isEqualTo(3)
    }

    @Test
    fun `a background update never wipes a stored route`() = runTest {
        source.addWorkout(workout("run", daysAgo = 1, route = sampleRoute(daysAgo = 1)))
        sync.sync()

        visibility.isForeground = false
        source.routesAvailable = false
        val start = now.minus(Duration.ofDays(1))
        source.replaceStreams("run", WorkoutStreams.EMPTY.copy(heartRate = HeartRateSeries.of(start.toEpochMilli() to 150)))
        sync.sync()

        val saved = workouts.get(HealthSync.workoutId("run"))!!
        assertThat(saved.summary.hasRoute).isTrue()
        assertThat(workouts.streams(saved.id).route.size).isEqualTo(3)
        assertThat(workouts.streams(saved.id).heartRate.size).isEqualTo(1)
    }

    @Test
    fun `workouts from an older sync version are imported again`() = runTest {
        source.addWorkout(workout("a", daysAgo = 3))
        sync.sync()
        val reads = source.workoutReads

        syncState.put("sync.version", "1")
        sync.sync()

        assertThat(source.workoutReads > reads).isTrue()
        assertThat(stored()).containsExactly("a")
    }

    private fun sampleRoute(daysAgo: Long): Route {
        val start = now.minus(Duration.ofDays(daysAgo)).toEpochMilli()
        return Route(
            times = longArrayOf(start, start + 1_000, start + 2_000),
            latitudes = doubleArrayOf(55.0, 55.0001, 55.0002),
            longitudes = doubleArrayOf(12.0, 12.0, 12.0),
            altitudes = doubleArrayOf(Double.NaN, Double.NaN, Double.NaN),
            accuracies = doubleArrayOf(3.0, 3.0, 3.0),
        )
    }

    private suspend fun stored(): List<String> = workouts.observeAll().first().map { it.sourceRecordId }

    private fun workout(id: String, daysAgo: Long, activity: String = "running", route: Route? = Route.EMPTY): SourceWorkout {
        val start = now.minus(Duration.ofDays(daysAgo))
        return SourceWorkout(
            sourceRecordId = id,
            origin = "com.fitbit.FitbitMobile",
            device = "Sense",
            activity = activity,
            title = null,
            start = start,
            end = start.plus(Duration.ofMinutes(30)),
            zoneOffset = ZoneOffset.ofHours(2),
            lastModified = start,
            laps = emptyList(),
            segments = emptyList(),
            route = route,
        )
    }

    private fun rhr(id: String, daysAgo: Long, bpm: Double) = Measurement(
        sourceRecordId = id,
        kind = MeasurementKind.RESTING_HEART_RATE,
        time = now.minus(Duration.ofDays(daysAgo)),
        zoneOffset = ZoneOffset.ofHours(2),
        value = bpm,
        origin = "com.fitbit.FitbitMobile",
    )
}
