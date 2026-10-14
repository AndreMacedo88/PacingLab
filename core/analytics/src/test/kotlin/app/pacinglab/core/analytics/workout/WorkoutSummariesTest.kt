package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.WorkoutStreams
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class WorkoutSummariesTest {
    private val source = SourceWorkout(
        sourceRecordId = "1",
        origin = "com.fitbit.FitbitMobile",
        device = "Sense",
        activity = "running",
        title = "Run",
        start = T0,
        end = T0.plus(Duration.ofMinutes(30)),
        zoneOffset = null,
        lastModified = T0,
        laps = emptyList(),
        segments = emptyList(),
    )

    // Two points 0.001 degrees of latitude apart: about 111 m.
    private val route = Route(
        times = longArrayOf(0, 30_000),
        latitudes = doubleArrayOf(55.0, 55.001),
        longitudes = doubleArrayOf(12.0, 12.0),
        altitudes = doubleArrayOf(Double.NaN, Double.NaN),
        accuracies = doubleArrayOf(Double.NaN, Double.NaN),
    )

    @Test
    fun `reported distance wins over route and speed`() {
        val streams = WorkoutStreams(HeartRateSeries.EMPTY, speedBlocks(10 to 3.0), route, reportedDistanceMeters = 5_000.0)

        val summary = WorkoutSummaries.summarize(source, streams, Sport.RUN)

        assertThat(summary.distanceMeters).isEqualTo(5_000.0)
        assertThat(summary.hasRoute).isTrue()
        assertThat(summary.durationMillis).isEqualTo(30 * 60_000L)
    }

    @Test
    fun `route length is used when no total was reported`() {
        val streams = WorkoutStreams(HeartRateSeries.EMPTY, SampleSeries.EMPTY, route, reportedDistanceMeters = null)

        assertThat(WorkoutSummaries.summarize(source, streams, Sport.RUN).distanceMeters).isNotNull().isCloseTo(111.2, 0.5)
    }

    @Test
    fun `speed is integrated when there is neither total nor route, ignoring spikes`() {
        val speed = speedBlocks(101 to 4.0, 3 to 88.0)
        val streams = WorkoutStreams(HeartRateSeries.EMPTY, speed, Route.EMPTY, reportedDistanceMeters = null)

        val summary = WorkoutSummaries.summarize(source, streams, Sport.RUN)

        // 100 one-second intervals at 4 m/s; the spike samples are dropped before integrating.
        assertThat(summary.distanceMeters).isNotNull().isCloseTo(400.0, 1e-6)
        assertThat(summary.hasRoute).isFalse()
    }

    @Test
    fun `no data means no distance or heart rate`() {
        val summary = WorkoutSummaries.summarize(source, WorkoutStreams.EMPTY, Sport.RUN)

        assertThat(summary.distanceMeters).isNull()
        assertThat(summary.averageHr).isNull()
        assertThat(summary.maxHr).isNull()
    }

    @Test
    fun `average heart rate is weighted by time and skips dropouts`() {
        val hr = HeartRateSeries.of(0L to 100, 1_000L to 200, 4_000L to 150, 100_000L to 150)

        // 100 for 1 s, 200 for 3 s; the 96 s gap is a dropout and is ignored.
        assertThat(WorkoutSummaries.timeWeightedAverage(hr)).isEqualTo(175)
    }

    @Test
    fun `a single-sample spike does not set the maximum`() {
        val hr = HeartRateSeries.of(0L to 160, 1_000L to 162, 2_000L to 230, 3_000L to 163, 4_000L to 164, 5_000L to 165)

        assertThat(WorkoutSummaries.spikeResistantMax(hr)).isEqualTo(164)
    }
}
