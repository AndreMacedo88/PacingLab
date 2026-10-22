package app.pacinglab.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.analytics.performance.Vo2MaxSource
import app.pacinglab.core.data.testPreferences
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.EffortStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.HeartRateSeries
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
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
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
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class PerformanceRepositoryTest {
    private val cest = ZoneOffset.ofHours(2)
    private val now = Instant.parse("2026-10-21T10:00:00Z")
    private lateinit var database: PacingLabDatabase
    private lateinit var workouts: WorkoutStore
    private lateinit var measurements: MeasurementStore
    private lateinit var repository: PerformanceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
        workouts = WorkoutStore(database)
        measurements = MeasurementStore(database)
        val physiology = PhysiologyStore(database)
        val clock = Clock.fixed(now, cest)
        val preferences = testPreferences()
        val load =
            TrainingLoadRepository(
                workouts,
                physiology,
                NightRepository(measurements, preferences, clock),
                preferences,
                EffortRepository(EffortStore(database)),
                clock,
            )
        repository = PerformanceRepository(workouts, measurements, load, clock)
        runTest { physiology.save(PhysiologyEntry(LocalDate.of(2026, 1, 1), maxHr = 190, thresholdHr = 170, restingHr = 50)) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `runs, Fitbit values and tests each become points of their own source`() = runTest {
        saveSteadyRun("run", Instant.parse("2026-10-20T16:00:00Z"))
        measurements.saveMeasurements(
            listOf(
                Measurement(
                    "fitbit-1",
                    MeasurementKind.VO2_MAX,
                    Instant.parse("2026-10-20T16:31:00Z"),
                    cest,
                    55.2,
                    "com.fitbit.FitbitMobile",
                ),
            ),
        )
        repository.saveTest(LocalDate.of(2026, 10, 18), 57.0)

        val history = repository.observeVo2Max().first()

        assertThat(history.points.map { it.source }).containsExactly(Vo2MaxSource.TEST, Vo2MaxSource.PACINGLAB, Vo2MaxSource.FITBIT)
        // 4.0 m/s at 160 bpm with HRmax 190 and resting 50.
        assertThat(history.points.single { it.source == Vo2MaxSource.PACINGLAB }.value).isCloseTo(3.5 + 48.0 / (110.0 / 140), 1e-6)
        assertThat(history.trend).isNotNull()
    }

    @Test
    fun `a deleted test is gone`() = runTest {
        repository.saveTest(LocalDate.of(2026, 10, 18), 57.0)
        repository.deleteTest(LocalDate.of(2026, 10, 18))

        assertThat(repository.observeVo2Max().first().points).isEmpty()
    }

    /** Twenty minutes at 4.0 m/s and 160 bpm, one sample a second. */
    private suspend fun saveSteadyRun(id: String, start: Instant) {
        val n = 20 * 60
        val times = LongArray(n) { start.toEpochMilli() + it * 1_000L }
        workouts.save(
            Workout(
                id = "hc:$id",
                source = DataSource.HEALTH_CONNECT,
                sourceRecordId = id,
                origin = "com.fitbit.FitbitMobile",
                device = null,
                activity = "running",
                title = "Run",
                start = start,
                end = start.plus(Duration.ofSeconds(n.toLong())),
                zoneOffset = cest,
                lastModified = start,
                sport = Sport.RUN,
                summary = WorkoutSummary(n * 1_000L, 4_800.0, 160, 160, hasRoute = false, hasSpeed = true),
                laps = emptyList(),
                segments = emptyList(),
                recordingMethod = RecordingMethod.ACTIVE,
            ),
            WorkoutStreams(HeartRateSeries(times, IntArray(n) { 160 }), SampleSeries(times, DoubleArray(n) { 4.0 }), Route.EMPTY, null),
        )
    }
}
