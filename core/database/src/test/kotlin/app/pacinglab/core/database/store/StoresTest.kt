package app.pacinglab.core.database.store

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Lap
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.WorkoutSummary
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class StoresTest {
    private lateinit var database: PacingLabDatabase
    private lateinit var workouts: WorkoutStore
    private lateinit var measurements: MeasurementStore
    private lateinit var syncState: SyncStateStore
    private lateinit var physiology: PhysiologyStore

    private val t0 = Instant.parse("2026-10-08T17:34:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
        workouts = WorkoutStore(database)
        measurements = MeasurementStore(database)
        syncState = SyncStateStore(database)
        physiology = PhysiologyStore(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `workout and streams survive a round trip`() = runTest {
        val workout = workout("hc:a", minutesFromStart = 0)
        val streams = WorkoutStreams(
            heartRate = HeartRateSeries.of(t0.toEpochMilli() to 120, t0.toEpochMilli() + 1_000 to 125),
            speed = SampleSeries(longArrayOf(t0.toEpochMilli()), doubleArrayOf(3.25)),
            route = Route(
                longArrayOf(t0.toEpochMilli()),
                doubleArrayOf(55.1),
                doubleArrayOf(12.2),
                doubleArrayOf(Double.NaN),
                doubleArrayOf(4.0),
            ),
            reportedDistanceMeters = null,
            steps = SampleSeries(longArrayOf(t0.toEpochMilli(), t0.toEpochMilli() + 60_000), doubleArrayOf(0.0, 171.5)),
        )

        workouts.save(workout, streams)

        assertThat(workouts.get("hc:a")).isEqualTo(workout)
        val loaded = workouts.streams("hc:a")
        assertThat(loaded.heartRate).isEqualTo(streams.heartRate)
        assertThat(loaded.speed).isEqualTo(streams.speed)
        assertThat(loaded.steps).isEqualTo(streams.steps)
        assertThat(loaded.route.latitudes.toList()).containsExactly(55.1)
        assertThat(workouts.heartRate("hc:a")).isEqualTo(streams.heartRate)
        assertThat(workouts.heartRate("hc:missing").isEmpty()).isEqualTo(true)
    }

    @Test
    fun `saving again replaces streams instead of accumulating them`() = runTest {
        val workout = workout("hc:a", minutesFromStart = 0)
        workouts.save(workout, WorkoutStreams.EMPTY.copy(heartRate = HeartRateSeries.of(1L to 100)))
        workouts.save(workout, WorkoutStreams.EMPTY)

        assertThat(workouts.streams("hc:a").heartRate.isEmpty()).isEqualTo(true)
    }

    @Test
    fun `workouts are listed newest first and found by overlap`() = runTest {
        workouts.save(workout("hc:old", minutesFromStart = 0), WorkoutStreams.EMPTY)
        workouts.save(workout("hc:new", minutesFromStart = 120), WorkoutStreams.EMPTY)

        assertThat(workouts.observeAll().first().map { it.id }).containsExactly("hc:new", "hc:old")
        val found = workouts.overlapping(t0.plusSeconds(10 * 60), t0.plusSeconds(11 * 60))
        assertThat(found.map { it.id }).containsExactly("hc:old")
    }

    @Test
    fun `deleting by source id removes the workout and its streams`() = runTest {
        workouts.save(workout("hc:a", minutesFromStart = 0), WorkoutStreams.EMPTY.copy(heartRate = HeartRateSeries.of(1L to 100)))

        assertThat(workouts.deleteBySourceIds(DataSource.HEALTH_CONNECT, listOf("a", "unknown"))).isEqualTo(1)
        assertThat(workouts.get("hc:a")).isNull()
        assertThat(workouts.streams("hc:a").heartRate.isEmpty()).isEqualTo(true)
    }

    @Test
    fun `measurements are upserted by source id and deleted by id`() = runTest {
        val rhr = Measurement("m1", MeasurementKind.RESTING_HEART_RATE, t0, ZoneOffset.ofHours(2), 52.0, "com.fitbit.FitbitMobile")
        measurements.saveMeasurements(listOf(rhr))
        measurements.saveMeasurements(listOf(rhr.copy(value = 50.0)))

        val range = measurements.observeMeasurements(MeasurementKind.RESTING_HEART_RATE, t0.minusSeconds(1), t0.plusSeconds(1))
        assertThat(range.first().map { it.value }).containsExactly(50.0)

        measurements.delete(listOf("m1"))
        assertThat(range.first()).isEmpty()
    }

    @Test
    fun `sync state stores and removes values`() = runTest {
        syncState.put("token.WORKOUTS", "abc")
        assertThat(syncState.get("token.WORKOUTS")).isEqualTo("abc")
        syncState.remove("token.WORKOUTS")
        assertThat(syncState.get("token.WORKOUTS")).isNull()
    }

    @Test
    fun `physiology entries are saved, moved to another date and deleted`() = runTest {
        val first = PhysiologyEntry(LocalDate.of(2025, 8, 14), maxHr = 186)
        val second = PhysiologyEntry(LocalDate.of(2026, 10, 1), maxHr = 186, thresholdHr = 170, restingHr = 52)
        physiology.save(first)
        physiology.save(second)
        assertThat(physiology.observe().first().entries).containsExactly(first, second)

        val moved = second.copy(effectiveFrom = LocalDate.of(2026, 10, 5))
        physiology.save(moved, replacing = second.effectiveFrom)
        assertThat(physiology.observe().first().entries).containsExactly(first, moved)

        physiology.delete(first.effectiveFrom)
        assertThat(physiology.observe().first().entries).containsExactly(moved)
    }

    @Test
    fun `saving on a date that already has an entry overwrites it`() = runTest {
        val day = LocalDate.of(2026, 10, 1)
        physiology.save(PhysiologyEntry(day, maxHr = 184))
        physiology.save(PhysiologyEntry(day, maxHr = 186))

        assertThat(physiology.observe().first().entries.single().maxHr).isEqualTo(186)
    }

    private fun workout(id: String, minutesFromStart: Long): Workout {
        val start = t0.plusSeconds(minutesFromStart * 60)
        return Workout(
            id = id,
            source = DataSource.HEALTH_CONNECT,
            sourceRecordId = id.removePrefix("hc:"),
            origin = "com.fitbit.FitbitMobile",
            device = "Sense",
            activity = "running",
            title = "Run",
            start = start,
            end = start.plusSeconds(30 * 60),
            zoneOffset = ZoneOffset.ofHours(2),
            lastModified = start,
            sport = Sport.RUN,
            summary = WorkoutSummary(30 * 60_000L, 5_000.0, 150, 176, hasRoute = true, hasSpeed = true),
            laps = listOf(Lap(start, start.plusSeconds(300), 1_000.0)),
            segments = emptyList(),
            recordingMethod = RecordingMethod.ACTIVE,
        )
    }
}
