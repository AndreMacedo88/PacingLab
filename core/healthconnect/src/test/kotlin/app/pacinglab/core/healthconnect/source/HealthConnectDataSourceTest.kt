package app.pacinglab.core.healthconnect.source

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Velocity
import app.pacinglab.core.healthconnect.HealthConnectPermissions
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.SegmentKind
import app.pacinglab.core.model.source.ChangeChannel
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
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
class HealthConnectDataSourceTest {
    private val t0 = Instant.parse("2026-10-08T17:34:00Z")
    private val offset = ZoneOffset.ofHours(2)
    private val fitbit = FITBIT
    private val googleFit = "com.google.android.apps.fitness"

    private val client = FakeHealthConnectGateway()
    private val source = HealthConnectDataSource(client, Clock.fixed(t0.plus(Duration.ofDays(1)), ZoneOffset.UTC))

    @Test
    fun `workouts are mapped with activity, laps and segments`() = runTest {
        client.insert(session("s1", minute = 0, length = 6, type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))

        val workouts = source.readWorkouts(t0.minusSeconds(60), t0.plus(Duration.ofHours(1)))

        assertThat(workouts.size).isEqualTo(1)
        val run = workouts.single()
        assertThat(run.sourceRecordId).isEqualTo("s1")
        assertThat(run.activity).isEqualTo("running")
        assertThat(run.origin).isEqualTo(FITBIT)
        assertThat(run.laps.single().lengthMeters).isEqualTo(1_000.0)
        assertThat(run.segments.map { it.kind }).containsExactly(SegmentKind.RUNNING, SegmentKind.PAUSE)
    }

    @Test
    fun `the recording method separates watch workouts from detected ones`() = runTest {
        client.insert(session("watch", minute = 0, length = 30))
        client.insert(
            session(
                "detected",
                minute = 40,
                length = 30,
                origin = googleFit,
                recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED,
            ),
        )
        client.insert(session("typed", minute = 80, length = 30, recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY))
        client.insert(session("unknown", minute = 120, length = 30, recordingMethod = Metadata.RECORDING_METHOD_UNKNOWN))

        val methods = source.readWorkouts(t0, t0.plus(Duration.ofHours(3))).associate { it.sourceRecordId to it.recordingMethod }

        assertThat(methods).isEqualTo(
            mapOf(
                "watch" to RecordingMethod.ACTIVE,
                "detected" to RecordingMethod.AUTOMATIC,
                "typed" to RecordingMethod.MANUAL,
                "unknown" to RecordingMethod.UNKNOWN,
            ),
        )
    }

    @Test
    fun `only workouts starting inside the window are returned`() = runTest {
        client.insert(
            *arrayOf<Record>(
                session("before", minute = -120, length = 30),
                session("inside", minute = 10, length = 30),
                session("after", minute = 90, length = 30),
            ),
        )

        val workouts = source.readWorkouts(t0, t0.plus(Duration.ofMinutes(60)))

        assertThat(workouts.map { it.sourceRecordId }).containsExactly("inside")
    }

    @Test
    fun `heart rate outside workouts is read for one app and clipped to the span`() = runTest {
        client.insert(
            heartRate("night1", fitbit, from = t0.minusSeconds(30), seconds = 60, bpm = 52),
            heartRate("night2", fitbit, from = t0.plusSeconds(120), seconds = 60, bpm = 50),
            heartRate("other", googleFit, from = t0, seconds = 60, bpm = 99),
        )

        val series = source.readHeartRate(t0, t0.plusSeconds(600), fitbit)

        assertThat(series.size).isEqualTo(30 + 60)
        assertThat(series.bpm.none { it == 99 }).isTrue()
        assertThat(series.times.first()).isEqualTo(t0.toEpochMilli())
    }

    @Test
    fun `heart rate is clipped to the workout, includes records starting before it, and ignores other apps`() = runTest {
        val workout = session("s1", minute = 0, length = 10, type = ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP)
        client.insert(
            *arrayOf<Record>(
                workout,
                // A one-minute record that starts 30 s before the workout.
                heartRate("hr1", fitbit, from = t0.minusSeconds(30), seconds = 60, bpm = 120),
                heartRate("hr2", fitbit, from = t0.plusSeconds(30), seconds = 60, bpm = 150),
                heartRate("other", googleFit, from = t0.plusSeconds(30), seconds = 60, bpm = 99),
            ),
        )
        val listed = source.readWorkouts(t0.minusSeconds(60), t0.plusSeconds(3_600))

        val streams = source.readStreams(listed).getValue(listed.single().sourceRecordId)

        val times = streams.heartRate.times
        assertThat(times.first()).isEqualTo(t0.toEpochMilli())
        assertThat(times.all { it in t0.toEpochMilli()..t0.plusSeconds(600).toEpochMilli() }).isTrue()
        assertThat(streams.heartRate.bpm.none { it == 99 }).isTrue()
        assertThat(streams.heartRate.size).isEqualTo(30 + 60)
    }

    @Test
    fun `speed, route and reported distance come with the streams`() = runTest {
        val route = ExerciseRoute(
            List(10) { i ->
                ExerciseRoute.Location(
                    time = t0.plusSeconds(i * 10L),
                    latitude = 55.0 + i * 1e-4,
                    longitude = 12.0,
                    altitude = Length.meters(10.0 + i),
                )
            },
        )
        client.insert(
            *arrayOf<Record>(
                session("s1", minute = 0, length = 2, route = route),
                SpeedRecord(
                    startTime = t0,
                    startZoneOffset = offset,
                    endTime = t0.plusSeconds(60),
                    endZoneOffset = offset,
                    samples = List(60) { SpeedRecord.Sample(t0.plusSeconds(it.toLong()), Velocity.metersPerSecond(4.0)) },
                    metadata = metadata("sp1", fitbit),
                ),
                // Half of this record overlaps the two-minute workout.
                DistanceRecord(
                    startTime = t0.plusSeconds(60),
                    startZoneOffset = offset,
                    endTime = t0.plusSeconds(180),
                    endZoneOffset = offset,
                    distance = Length.meters(400.0),
                    metadata = metadata("d1", fitbit),
                ),
                DistanceRecord(
                    startTime = t0,
                    startZoneOffset = offset,
                    endTime = t0.plusSeconds(60),
                    endZoneOffset = offset,
                    distance = Length.meters(999.0),
                    metadata = metadata("d2", googleFit),
                ),
            ),
        )
        val listed = source.readWorkouts(t0.minusSeconds(60), t0.plusSeconds(3_600))

        val streams = source.readStreams(listed).getValue(listed.single().sourceRecordId)

        assertThat(streams.speed.size).isEqualTo(60)
        assertThat(streams.route.size).isEqualTo(10)
        assertThat(streams.route.altitudes[3]).isEqualTo(13.0)
        assertThat(streams.reportedDistanceMeters).isNotNull().isCloseTo(200.0, 1e-6)
    }

    @Test
    fun `measurements are mapped by kind`() = runTest {
        client.insert(
            *arrayOf<Record>(
                RestingHeartRateRecord(
                    time = t0,
                    zoneOffset = offset,
                    beatsPerMinute = 48,
                    metadata = metadata("rhr", fitbit),
                ),
            ),
        )

        val values = source.readMeasurements(MeasurementKind.RESTING_HEART_RATE, t0.minusSeconds(60), t0.plusSeconds(60))

        assertThat(values.map { it.value }).containsExactly(48.0)
        assertThat(values.single().sourceRecordId).isEqualTo("rhr")
        assertThat(values.single().origin).isEqualTo(FITBIT)
    }

    @Test
    fun `changes report new workouts and deletions`() = runTest {
        val token = source.changesToken(ChangeChannel.WORKOUTS)
        client.insert(session("s1", minute = 0, length = 30))

        val added = source.readChanges(ChangeChannel.WORKOUTS, token)
        assertThat(added.workouts.map { it.sourceRecordId }).containsExactly("s1")

        client.delete(ExerciseSessionRecord::class, "s1")
        val removed = source.readChanges(ChangeChannel.WORKOUTS, added.nextToken)
        assertThat(removed.deletedRecordIds).containsExactly("s1")
        assertThat(removed.workouts).isEmpty()
    }

    @Test
    fun `a workout deleted and rewritten under the same id is reported as changed, not deleted`() = runTest {
        client.insert(session("warm-up", minute = 0, length = 6))
        val token = source.changesToken(ChangeChannel.WORKOUTS)

        client.delete(ExerciseSessionRecord::class, "warm-up")
        client.insert(session("warm-up", minute = 0, length = 6), session("intervals", minute = 7, length = 36))
        val batch = source.readChanges(ChangeChannel.WORKOUTS, token)

        assertThat(batch.workouts.map { it.sourceRecordId }).containsExactlyInAnyOrder("warm-up", "intervals")
        assertThat(batch.deletedRecordIds).isEmpty()
    }

    @Test
    fun `a workout written and deleted between two syncs is reported as deleted only`() = runTest {
        val token = source.changesToken(ChangeChannel.WORKOUTS)

        client.insert(session("false-start", minute = 0, length = 1))
        client.delete(ExerciseSessionRecord::class, "false-start")
        val batch = source.readChanges(ChangeChannel.WORKOUTS, token)

        assertThat(batch.workouts).isEmpty()
        assertThat(batch.deletedRecordIds).containsExactly("false-start")
    }

    @Test
    fun `heart rate changes are reported as time spans`() = runTest {
        val token = source.changesToken(ChangeChannel.HEART_RATE)
        client.insert(heartRate("hr1", fitbit, from = t0, seconds = 60, bpm = 120))

        val batch = source.readChanges(ChangeChannel.HEART_RATE, token)

        assertThat(batch.affectedSpans).containsExactly(t0..t0.plusSeconds(60))
    }

    @Test
    fun `channels follow granted permissions`() = runTest {
        assertThat(source.availableChannels()).isEqualTo(ChangeChannel.entries.toSet())

        client.granted = HealthConnectPermissions.all - HealthPermission.getReadPermission(SpeedRecord::class)

        assertThat(source.availableChannels().contains(ChangeChannel.SPEED)).isFalse()
    }

    @Test
    fun `routes come with the bulk session read, without single-record reads`() = runTest {
        val route = ExerciseRoute(listOf(ExerciseRoute.Location(t0.plusSeconds(5), 55.0, 12.0)))
        client.insert(session("run", minute = 0, length = 30, route = route))

        val listed = source.readWorkouts(t0, t0.plus(Duration.ofHours(1)))
        source.readStreams(listed)

        assertThat(listed.single().route?.size).isEqualTo(1)
        assertThat(client.singleReads).isEmpty()
    }

    @Test
    fun `heart rate falls back to another app when the recording app wrote none`() = runTest {
        client.insert(
            session("ride", minute = 0, length = 10, type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING, origin = googleFit),
            heartRate("watch", fitbit, from = t0.plusSeconds(10), seconds = 60, bpm = 130),
        )
        val listed = source.readWorkouts(t0.minusSeconds(60), t0.plusSeconds(3_600))

        val streams = source.readStreams(listed).getValue("ride")

        assertThat(streams.heartRate.size).isEqualTo(60)
    }

    @Test
    fun `rate limit errors are recognised through the cause chain`() {
        val wrapped = IllegalStateException("Unexpected", RuntimeException("API call quota exceeded, availableQuota: 0.5 requested: 1"))

        assertThat(wrapped.isRateLimit()).isTrue()
        assertThat(IllegalStateException("something else").isRateLimit()).isFalse()
    }

    @Test
    fun `earliest workout time is the oldest session start`() = runTest {
        client.insert(session("later", minute = 60, length = 10), session("first", minute = -600, length = 10))

        assertThat(source.earliestWorkoutTime()).isEqualTo(t0.minus(Duration.ofMinutes(600)))
    }

    private fun metadata(id: String, origin: String, recordingMethod: Int = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED): Metadata =
        FakeHealthConnectGateway.metadata(id, origin, recordingMethod = recordingMethod)

    private fun session(
        id: String,
        minute: Long,
        length: Long,
        type: Int = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        route: ExerciseRoute? = null,
        origin: String = fitbit,
        recordingMethod: Int = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED,
    ): ExerciseSessionRecord {
        val start = t0.plus(Duration.ofMinutes(minute))
        val end = start.plus(Duration.ofMinutes(length))
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            metadata = metadata(id, origin, recordingMethod),
            exerciseType = type,
            title = null,
            notes = null,
            // Health Connect only accepts segment types that suit the session type.
            segments = listOfNotNull(
                ExerciseSegment(start, end.minusSeconds(1), ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING)
                    .takeIf { type == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING },
                ExerciseSegment(end.minusSeconds(1), end, ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE),
            ),
            laps = listOf(ExerciseLap(start, start.plusSeconds(60), Length.meters(1_000.0))),
            exerciseRoute = route,
        )
    }

    private fun heartRate(id: String, origin: String, from: Instant, seconds: Long, bpm: Long) = HeartRateRecord(
        startTime = from,
        startZoneOffset = offset,
        endTime = from.plusSeconds(seconds),
        endZoneOffset = offset,
        samples = List(seconds.toInt()) { HeartRateRecord.Sample(from.plusSeconds(it.toLong()), bpm) },
        metadata = metadata(id, origin),
    )

    private companion object {
        const val FITBIT = "com.fitbit.FitbitMobile"
    }
}
