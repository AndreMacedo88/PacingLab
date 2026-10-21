package app.pacinglab.core.analytics.intervals

import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Random

class IntervalDetectionTest {
    private val start = 1_792_000_000_000L

    @Test
    fun `reps with walking recoveries are found with their length and distance`() {
        // 5 x 4 min at 4:00/km with 2 min walking, as recorded by a wrist sensor.
        val blocks = (1..5).flatMap { listOf(240 to 4.17, 120 to 1.1) }.dropLast(1)

        val phases = detect(noisy(blocks, seed = 1), mode = RecordingMode.INTERVALS)

        assertThat(phases).isNotNull()
        val reps = phases!!.filter { it.kind == PhaseKind.WORK }
        assertThat(phases.map { it.kind }).isEqualTo(alternating(reps = 5))
        assertThat(reps.map { it.durationMillis / 1000 }).each { it.isBetween(234L, 246L) }
        assertThat(reps.map { it.distanceMeters }).each { it.isBetween(960.0, 1_040.0) }
    }

    @Test
    fun `jogged recoveries are told apart from reps`() {
        // 6 x 5 min at 4:40/km with 1 min jogged at about 11:00/km.
        val blocks = (1..6).flatMap { listOf(300 to 3.6, 60 to 1.5) }.dropLast(1)

        val phases = detect(noisy(blocks, seed = 2), mode = RecordingMode.INTERVALS)

        assertThat(phases?.count { it.kind == PhaseKind.WORK }).isEqualTo(6)
        assertThat(phases?.count { it.kind == PhaseKind.RECOVERY }).isEqualTo(5)
    }

    @Test
    fun `a short jog after the last recovery is cool-down, not a rep`() {
        val blocks = (1..5).flatMap { listOf(240 to 4.17, 120 to 1.1) } + listOf(20 to 3.3)

        val phases = detect(noisy(blocks, seed = 3), mode = RecordingMode.INTERVALS)

        assertThat(phases?.map { it.kind }).isEqualTo(alternating(reps = 5) + PhaseKind.COOL_DOWN)
    }

    @Test
    fun `easy running around the reps of a plain run becomes warm-up and cool-down`() {
        val reps = (1..5).flatMap { listOf(180 to 4.5, 90 to 2.0) }.dropLast(1)
        val blocks = listOf(600 to 3.0) + reps + listOf(300 to 3.0)

        val phases = detect(noisy(blocks, seed = 4), mode = RecordingMode.CONTINUOUS)

        assertThat(phases?.map { it.kind }).isEqualTo(listOf(PhaseKind.WARM_UP) + alternating(reps = 5) + PhaseKind.COOL_DOWN)
        assertThat(phases!!.first().durationMillis / 1000).isBetween(594L, 606L)
    }

    @Test
    fun `phases cover the workout without gaps or overlaps`() {
        val blocks = listOf(300 to 3.0) + (1..4).flatMap { listOf(200 to 4.4, 100 to 1.2) } + listOf(200 to 3.0)
        val series = noisy(blocks, seed = 5)
        val end = start + blocks.sumOf { it.first } * 1000L

        val phases = detect(series, mode = RecordingMode.INTERVALS, end = end)!!

        assertThat(phases.first().startMillis).isEqualTo(start)
        assertThat(phases.last().endMillis).isEqualTo(end)
        assertThat(phases.zipWithNext().all { (a, b) -> a.endMillis == b.startMillis }).isEqualTo(true)
    }

    @Test
    fun `a steady run is not split, whatever the recording mode`() {
        val steady = noisy(listOf(1_800 to 3.3), seed = 6, noise = 0.25)

        assertThat(detect(steady, mode = RecordingMode.INTERVALS)).isNull()
        assertThat(detect(steady, mode = RecordingMode.CONTINUOUS)).isNull()
    }

    @Test
    fun `stops at traffic lights do not turn a plain run into intervals`() {
        val blocks = listOf(500 to 3.3, 30 to 0.0, 700 to 3.3, 45 to 0.0, 400 to 3.3, 20 to 0.0, 400 to 3.3)

        assertThat(detect(noisy(blocks, seed = 7), mode = RecordingMode.CONTINUOUS)).isNull()
    }

    @Test
    fun `a gym circuit timed in interval mode is not running intervals`() {
        val blocks = (1..8).flatMap { listOf(120 to 0.4, 60 to 0.05) }

        assertThat(detect(noisy(blocks, seed = 8, noise = 0.03), mode = RecordingMode.INTERVALS)).isNull()
    }

    @Test
    fun `single-sample spikes do not create reps`() {
        val blocks = (1..4).flatMap { listOf(240 to 4.0, 120 to 1.2) }.dropLast(1)
        val clean = noisy(blocks, seed = 9)
        val values = clean.values.copyOf()
        for (i in 30 until values.size step 97) values[i] = 10.5

        val phases = detect(SampleSeries(clean.times, values), mode = RecordingMode.INTERVALS)

        assertThat(phases?.count { it.kind == PhaseKind.WORK }).isEqualTo(4)
    }

    @Test
    fun `a sparse speed stream is not analysed`() {
        val blocks = (1..5).flatMap { listOf(240 to 4.17, 120 to 1.1) }.dropLast(1)
        val dense = noisy(blocks, seed = 10)
        val keep = dense.times.indices.filter { it % 10 == 0 }
        val sparse = SampleSeries(LongArray(keep.size) { dense.times[keep[it]] }, DoubleArray(keep.size) { dense.values[keep[it]] })

        assertThat(detect(sparse, mode = RecordingMode.INTERVALS, end = start + blocks.sumOf { it.first } * 1000L)).isNull()
    }

    @Test
    fun `short gaps in the stream are bridged`() {
        val blocks = (1..5).flatMap { listOf(240 to 4.17, 120 to 1.1) }.dropLast(1)
        val dense = noisy(blocks, seed = 11)
        // Fitbit streams skip a second now and then.
        val keep = dense.times.indices.filter { it % 7 != 3 }
        val gappy = SampleSeries(LongArray(keep.size) { dense.times[keep[it]] }, DoubleArray(keep.size) { dense.values[keep[it]] })

        val phases = detect(gappy, mode = RecordingMode.INTERVALS, end = start + blocks.sumOf { it.first } * 1000L)

        assertThat(phases?.count { it.kind == PhaseKind.WORK }).isEqualTo(5)
    }

    @Test
    fun `recording mode follows the activity type`() {
        assertThat(RecordingMode.of("boot_camp")).isEqualTo(RecordingMode.INTERVALS)
        assertThat(RecordingMode.of("high_intensity_interval_training")).isEqualTo(RecordingMode.INTERVALS)
        assertThat(RecordingMode.of("running")).isEqualTo(RecordingMode.CONTINUOUS)
    }

    @Test
    fun `otsu separates two groups at the gap between them`() {
        val split = IntervalDetection.otsu(doubleArrayOf(1.0, 1.1, 0.9, 1.0, 4.0, 4.2, 3.9, 4.1))

        assertThat(split?.threshold).isNotNull().isBetween(1.1, 3.9)
        assertThat(split?.lowMean).isEqualTo(1.0)
    }

    @Test
    fun `running median ignores missing values`() {
        val smoothed = IntervalDetection.runningMedian(doubleArrayOf(1.0, Double.NaN, 3.0, 9.0, 3.0), halfWindow = 1)

        assertThat(smoothed.toList()).containsExactly(1.0, 2.0, 6.0, 3.0, 6.0)
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `any regular session is split into the reps it was built from`() = runTest {
        val config = PropTestConfig(seed = 20_261_021)
        checkAll(
            config,
            Arb.int(2..10),
            Arb.int(30..360),
            Arb.int(30..180),
            Arb.int(350..600),
            Arb.int(80..250),
            Arb.long(),
        ) { count, repSeconds, recoverySeconds, repCentimetres, recoveryCentimetres, seed ->
            val repSpeed = repCentimetres / 100.0
            val recoverySpeed = recoveryCentimetres / 100.0
            val blocks = (1..count).flatMap { listOf(repSeconds to repSpeed, recoverySeconds to recoverySpeed) }.dropLast(1)

            val phases = detect(noisy(blocks, seed = seed, noise = 0.2), mode = RecordingMode.INTERVALS)

            assertThat(phases?.map { it.kind }).isEqualTo(alternating(count))
            assertThat(phases!!.filter { it.kind == PhaseKind.WORK }.map { it.durationMillis / 1000 })
                .each { it.isBetween(repSeconds - 6L, repSeconds + 6L) }
        }
    }

    private fun detect(speed: SampleSeries, mode: RecordingMode, end: Long = speed.times.last() + 1_000): List<Phase>? =
        IntervalDetection.detect(speed, start, end, mode)

    private fun alternating(reps: Int): List<PhaseKind> = (1..reps).flatMap { listOf(PhaseKind.WORK, PhaseKind.RECOVERY) }.dropLast(1)

    /** 1 Hz speed from (seconds, m/s) blocks with uniform noise of +-[noise] m/s, never negative. */
    private fun noisy(blocks: List<Pair<Int, Double>>, seed: Long, noise: Double = 0.3): SampleSeries {
        val random = Random(seed)
        val total = blocks.sumOf { it.first }
        val values = DoubleArray(total)
        var i = 0
        for ((seconds, speed) in blocks) {
            repeat(seconds) {
                values[i++] = maxOf(0.0, speed + (random.nextDouble() * 2 - 1) * noise)
            }
        }
        return SampleSeries(LongArray(total) { start + it * 1_000L }, values)
    }
}
