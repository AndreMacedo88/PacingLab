package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.load.HeartRateLoad
import app.pacinglab.core.analytics.workout.T0
import app.pacinglab.core.analytics.workout.workout
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.math.abs

class SessionAnalyzerTest {
    private val zones = Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 50))

    @Test
    fun `warm-up run, interval workout and cool-down run become one phase list`() {
        val warmUp = part(workout("warm-up", Sport.RUN, startMinute = 0, minutes = 6), constant(360, 3.0, hr = 140), distance = 1_080.0)
        val intervalBlocks = (1..5).flatMap { listOf(240 to 4.17, 120 to 1.1) }.dropLast(1)
        val intervals = part(
            workout("intervals", Sport.RUN, startMinute = 7, minutes = 28).copy(activity = "boot_camp"),
            blocks(startMinute = 7, intervalBlocks, workHr = 170, restHr = 145),
            distance = 5_500.0,
        )
        val coolDown = part(
            workout("cool-down", Sport.RUN, startMinute = 36, minutes = 3),
            constant(180, 2.8, hr = 150, startMinute = 36),
            distance = 500.0,
        )

        val analysis = SessionAnalyzer.analyze(listOf(coolDown, intervals, warmUp), listOf(zones))

        assertThat(analysis.isIntervalSession).isTrue()
        assertThat(analysis.phases.first().kind).isEqualTo(PhaseKind.WARM_UP)
        assertThat(analysis.phases.last().kind).isEqualTo(PhaseKind.COOL_DOWN)
        val work = analysis.totals(PhaseKind.WORK)!!
        assertThat(work.count).isEqualTo(5)
        assertThat(work.durationMillis / 1000).isBetween(1_180L, 1_220L)
        assertThat(work.averageHr).isEqualTo(170)
        assertThat(analysis.totals(PhaseKind.RECOVERY)!!.count).isEqualTo(4)
        assertThat(analysis.totals(PhaseKind.WARM_UP, PhaseKind.COOL_DOWN)!!.distanceMeters).isEqualTo(1_580.0)
        assertThat(analysis.sprints).isFalse()
        // Heart rate steps from 170 to 145 as each rep ends: a drop of 25 within the first minute.
        assertThat(analysis.phases.filter { it.kind == PhaseKind.RECOVERY }.map { it.hrDrop to it.hrDropSeconds }.distinct())
            .isEqualTo(listOf(25 to 60))
        assertThat(analysis.averageHrDrop).isEqualTo(25)
        analysis.phases.filter { it.kind == PhaseKind.WORK }.forEach { assertThat(it.topSpeed!!).isBetween(4.1, 4.2) }
    }

    @Test
    fun `a sprint session is split into sprints without heart rate recovery`() {
        val sprint = listOf(2 to 3.5, 10 to 8.5, 2 to 3.5, 150 to 1.1)
        val sprints = part(
            workout("sprints", Sport.RUN, startMinute = 0, minutes = 9).copy(activity = "boot_camp"),
            blocks(startMinute = 0, listOf(30 to 1.2) + sprint + sprint + sprint, workHr = 130, restHr = 120),
            distance = 900.0,
        )

        val analysis = SessionAnalyzer.analyze(listOf(sprints), listOf(zones))

        assertThat(analysis.sprints).isTrue()
        assertThat(analysis.totals(PhaseKind.WORK)!!.count).isEqualTo(3)
        assertThat(analysis.averageHrDrop).isNull()
    }

    @Test
    fun `phase distances of an interval workout add up to its recorded distance`() {
        val blocks = (1..4).flatMap { listOf(240 to 4.0, 120 to 1.0) }.dropLast(1)
        val recorded = blocks.sumOf { it.first * it.second } * 1.03
        val intervals = part(workout("intervals", Sport.RUN, 0, 22).copy(activity = "boot_camp"), blocks(0, blocks, 165, 140), recorded)

        val total = SessionAnalyzer.analyze(listOf(intervals)).phases.sumOf { it.distanceMeters!! }

        assertThat(abs(total - recorded)).isBetween(0.0, 0.001)
    }

    @Test
    fun `a far-off recorded distance is not forced onto the phases`() {
        val blocks = (1..4).flatMap { listOf(240 to 4.0, 120 to 1.0) }.dropLast(1)
        val measured = blocks.sumOf { it.first * it.second }
        val intervals = part(workout("intervals", Sport.RUN, 0, 22).copy(activity = "boot_camp"), blocks(0, blocks, 165, 140), measured * 2)

        val total = SessionAnalyzer.analyze(listOf(intervals)).phases.sumOf { it.distanceMeters!! }

        assertThat(abs(total - measured)).isBetween(0.0, 1.0)
    }

    @Test
    fun `a steady run has no phases but time in zones`() {
        val run = part(workout("run", Sport.RUN, 0, 30), constant(1_800, 3.3, hr = 150), distance = 5_940.0)

        val analysis = SessionAnalyzer.analyze(listOf(run), listOf(zones))

        assertThat(analysis.isIntervalSession).isFalse()
        assertThat(analysis.phases).isEmpty()
        assertThat(analysis.zones.single().totalMillis / 1000).isEqualTo(1_799L)
    }

    @Test
    fun `time in zones adds up the parts without bridging the pause between them`() {
        val first = part(workout("a", Sport.RUN, 0, 10), constant(600, 3.0, hr = 140), 1_800.0)
        val second = part(workout("b", Sport.RUN, 14, 10), constant(600, 3.0, hr = 175, startMinute = 14), 1_800.0)

        val distribution = SessionAnalyzer.analyze(listOf(first, second), listOf(zones)).zones.single()

        assertThat(distribution.totalMillis / 1000).isEqualTo(1_198L)
        assertThat(distribution.millis[zones.indexOf(140)] / 1000).isEqualTo(599L)
        assertThat(distribution.millis[zones.indexOf(175)] / 1000).isEqualTo(599L)
    }

    @Test
    fun `training load adds up the parts and needs a resting heart rate`() {
        val anchors = HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 50)
        val first = part(workout("a", Sport.RUN, 0, 10), constant(600, 3.0, hr = 140), 1_800.0)
        val second = part(workout("b", Sport.RUN, 14, 10), constant(600, 3.0, hr = 175, startMinute = 14), 1_800.0)

        val load = SessionAnalyzer.analyze(listOf(first, second), anchors = anchors).load!!

        val expected = HeartRateLoad.of(first.streams.heartRate, anchors)!! + HeartRateLoad.of(second.streams.heartRate, anchors)!!
        assertThat(load).isEqualTo(expected)
        assertThat(SessionAnalyzer.analyze(listOf(first, second), anchors = anchors.copy(restingHr = null)).load).isNull()
        assertThat(SessionAnalyzer.analyze(listOf(first, second)).load).isNull()
    }

    @Test
    fun `other sports are never split into reps`() {
        val blocks = (1..4).flatMap { listOf(200 to 8.0, 100 to 3.0) }
        val ride = part(workout("ride", Sport.RIDE, 0, 20), blocks(0, blocks, 150, 120), 6_000.0)

        assertThat(SessionAnalyzer.analyze(listOf(ride)).phases).isEmpty()
    }

    @Test
    fun `totals leave distance out when a phase has none and weight heart rate by duration`() {
        val analysis = SessionAnalysis(
            phases = listOf(
                PhaseSummary(PhaseKind.WORK, 0, 60_000, 300.0, 170, 175),
                PhaseSummary(PhaseKind.WORK, 120_000, 300_000, null, 160, 165),
            ),
            zones = emptyList(),
        )

        val totals = analysis.totals(PhaseKind.WORK)

        assertThat(totals).isNotNull()
        assertThat(totals!!.distanceMeters).isNull()
        assertThat(totals.averageHr).isEqualTo(163)
        assertThat(analysis.totals(PhaseKind.RECOVERY)).isNull()
    }

    private fun part(workout: Workout, streams: WorkoutStreams, distance: Double) =
        SessionPart(workout.copy(summary = workout.summary.copy(distanceMeters = distance)), streams)

    private fun constant(seconds: Int, speed: Double, hr: Int, startMinute: Long = 0) =
        blocks(startMinute, listOf(seconds to speed), hr, hr)

    /** 1 Hz speed and heart rate; heart rate is [workHr] while faster than 2.5 m/s, else [restHr]. */
    private fun blocks(startMinute: Long, blocks: List<Pair<Int, Double>>, workHr: Int, restHr: Int): WorkoutStreams {
        val start = T0.plus(Duration.ofMinutes(startMinute)).toEpochMilli()
        val speeds = blocks.flatMap { (seconds, speed) -> List(seconds) { speed } }
        val times = LongArray(speeds.size) { start + it * 1_000L }
        return WorkoutStreams(
            heartRate = HeartRateSeries(times, IntArray(speeds.size) { if (speeds[it] > 2.5) workHr else restHr }),
            speed = SampleSeries(times, speeds.toDoubleArray()),
            route = Route.EMPTY,
            reportedDistanceMeters = null,
        )
    }
}
