package app.pacinglab.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.pacinglab.core.analytics.load.HeartRateLoad
import app.pacinglab.core.database.PacingLabDatabase
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.WorkoutSummary
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.NEWEST_SDK])
class TrainingLoadRepositoryTest {
    private val cest = ZoneOffset.ofHours(2)
    private val now = Instant.parse("2026-10-21T10:00:00Z")
    private val today = LocalDate.of(2026, 10, 21)
    private lateinit var database: PacingLabDatabase
    private lateinit var workouts: WorkoutStore
    private lateinit var physiology: PhysiologyStore
    private lateinit var measurements: MeasurementStore
    private lateinit var repository: TrainingLoadRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PacingLabDatabase::class.java)
            .setDriver(AndroidSQLiteDriver())
            .build()
        workouts = WorkoutStore(database)
        physiology = PhysiologyStore(database)
        measurements = MeasurementStore(database)
        repository = TrainingLoadRepository(workouts, physiology, measurements, Clock.fixed(now, cest))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `counted workouts get scores from the anchors of their day`() = runTest {
        physiology.save(PhysiologyEntry(today.minusDays(30), maxHr = 186, restingHr = 57))
        save(workout("run", start = "2026-10-20T16:31:00Z", minutes = 30), bpm = 165)
        // Google Fit detected the same run: a duplicate.
        save(
            workout("detected", start = "2026-10-20T16:32:00Z", minutes = 38, origin = GOOGLE_FIT, method = RecordingMethod.AUTOMATIC),
            bpm = 160,
        )
        // The watch was left recording for a day and a half.
        save(workout("runaway", start = "2026-10-10T08:00:00Z", minutes = 36 * 60, sport = Sport.OTHER), bpm = 90)
        save(workout("accidental", start = "2026-10-12T08:00:00Z", minutes = 0), bpm = 120)

        val loads = repository.observeWorkoutLoads().first()

        assertThat(loads.map { it.workoutId }).containsExactly("hc:run")
        val anchors = HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 57)
        assertThat(loads.single().scores).isEqualTo(HeartRateLoad.of(heartRate(Instant.parse("2026-10-20T16:31:00Z"), 30, 165), anchors))
        assertThat(loads.single().date).isEqualTo(LocalDate.of(2026, 10, 20))
    }

    @Test
    fun `measured resting heart rate stands in for one the athlete has not set`() = runTest {
        physiology.save(PhysiologyEntry(today.minusDays(30), maxHr = 186))
        measurements.saveMeasurements(listOf(rhr("r1", "2026-10-19T06:00:00Z", 52.0), rhr("r2", "2026-10-20T06:00:00Z", 54.0)))
        save(workout("run", start = "2026-10-20T16:31:00Z", minutes = 30), bpm = 165)

        val load = repository.observeWorkoutLoads().first().single()

        val anchors = HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 53)
        assertThat(load.scores).isEqualTo(HeartRateLoad.of(heartRate(Instant.parse("2026-10-20T16:31:00Z"), 30, 165), anchors))
    }

    @Test
    fun `without heart rate values there is no load`() = runTest {
        save(workout("run", start = "2026-10-20T16:31:00Z", minutes = 30), bpm = 165)

        assertThat(repository.observeWorkoutLoads().first().single().scores).isNull()
        assertThat(repository.observeFitness().first()).isEqualTo(emptyList())
    }

    @Test
    fun `fitness runs from the first day with load to today`() = runTest {
        physiology.save(PhysiologyEntry(today.minusDays(30), maxHr = 186, restingHr = 57))
        save(workout("a", start = "2026-10-15T16:00:00Z", minutes = 40), bpm = 160)
        save(workout("b", start = "2026-10-20T16:00:00Z", minutes = 30), bpm = 170)

        val series = repository.observeFitness().first()

        assertThat(series.first().date).isEqualTo(LocalDate.of(2026, 10, 15))
        assertThat(series.last().date).isEqualTo(today)
        val loads = repository.observeWorkoutLoads().first()
        assertThat(series.first().load).isCloseTo(loads.first().scores!!.stressScore, 1e-9)
        assertThat(series.last().load).isEqualTo(0.0)
        assertThat(series.single { it.date == LocalDate.of(2026, 10, 20) }.load).isCloseTo(loads.last().scores!!.stressScore, 1e-9)
    }

    private suspend fun save(workout: Workout, bpm: Int) {
        val minutes = Duration.between(workout.start, workout.end).toMinutes().toInt()
        workouts.save(workout, WorkoutStreams.EMPTY.copy(heartRate = heartRate(workout.start, minutes.coerceAtMost(120), bpm)))
    }

    /** Samples every 5 s at [bpm]. */
    private fun heartRate(start: Instant, minutes: Int, bpm: Int): HeartRateSeries {
        val n = minutes * 12 + 1
        return HeartRateSeries(LongArray(n) { start.toEpochMilli() + it * 5_000L }, IntArray(n) { bpm })
    }

    private fun workout(
        id: String,
        start: String,
        minutes: Long,
        sport: Sport = Sport.RUN,
        origin: String = FITBIT,
        method: RecordingMethod = RecordingMethod.ACTIVE,
    ): Workout {
        val begin = Instant.parse(start)
        return Workout(
            id = "hc:$id",
            source = DataSource.HEALTH_CONNECT,
            sourceRecordId = id,
            origin = origin,
            device = null,
            activity = "running",
            title = null,
            start = begin,
            end = begin.plus(Duration.ofMinutes(minutes)),
            zoneOffset = cest,
            lastModified = begin,
            sport = sport,
            summary = WorkoutSummary(minutes * 60_000, null, 150, 170, hasRoute = false, hasSpeed = false),
            laps = emptyList(),
            segments = emptyList(),
            recordingMethod = method,
        )
    }

    private fun rhr(id: String, time: String, bpm: Double) = Measurement(
        sourceRecordId = id,
        kind = MeasurementKind.RESTING_HEART_RATE,
        time = Instant.parse(time),
        zoneOffset = cest,
        value = bpm,
        origin = FITBIT,
    )

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
        const val GOOGLE_FIT = "com.google.android.apps.fitness"
    }
}
