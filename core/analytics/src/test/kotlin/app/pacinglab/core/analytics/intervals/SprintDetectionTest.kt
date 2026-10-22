package app.pacinglab.core.analytics.intervals

import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.util.Random

class SprintDetectionTest {
    private val start = 1_792_000_000_000L

    @Test
    fun `short sprints with walking rests are found, acceleration included`() {
        // As on 18 October 2026: 10-second sprints with 2:30 walking, and a ramp up and down around each.
        val sprint = listOf(2 to 3.5, 10 to 8.5, 2 to 3.5)
        val blocks = listOf(60 to 1.2) + sprint + listOf(150 to 1.1) + sprint + listOf(150 to 1.1) + sprint + listOf(40 to 1.2)

        val phases = detect(noisy(blocks, seed = 3))

        assertThat(phases).isNotNull()
        assertThat(phases!!.map { it.kind }).containsExactly(
            PhaseKind.WARM_UP,
            PhaseKind.WORK,
            PhaseKind.RECOVERY,
            PhaseKind.WORK,
            PhaseKind.RECOVERY,
            PhaseKind.WORK,
            PhaseKind.COOL_DOWN,
        )
        val sprints = phases.filter { it.kind == PhaseKind.WORK }
        assertThat(sprints.map { it.durationMillis / 1000 }).each { it.isBetween(11L, 15L) }
        assertThat(sprints.map { it.distanceMeters }).each { it.isBetween(85.0, 105.0) }
    }

    @Test
    fun `six-second sprints with short rests still count`() {
        val sprint = listOf(6 to 7.5)
        val blocks = listOf(30 to 1.5) + (1..4).flatMap { sprint + listOf(50 to 1.5) }

        val phases = detect(noisy(blocks, seed = 4))

        assertThat(phases!!.count { it.kind == PhaseKind.WORK }).isEqualTo(4)
    }

    @Test
    fun `steady running, fast reps and a single sprint are not sprint sessions`() {
        // A fast continuous run: one burst far longer than a sprint.
        assertThat(detect(noisy(listOf(60 to 2.0, 600 to 5.2, 60 to 2.0), seed = 5))).isNull()
        // 400 m reps at 3:00/km: 72 s each.
        assertThat(detect(noisy((1..4).flatMap { listOf(72 to 5.6, 90 to 1.5) }, seed = 6))).isNull()
        // One strides at the end of an easy run.
        assertThat(detect(noisy(listOf(1_200 to 3.0, 12 to 7.0, 60 to 2.0), seed = 7))).isNull()
        // Reps at 4:00/km whose starts spike past sprint speed: running between the bursts.
        assertThat(detect(noisy((1..4).flatMap { listOf(8 to 6.0, 232 to 4.2, 60 to 1.3) }, seed = 9))).isNull()
        // Accelerations 10 seconds apart are a fartlek, not sprints with rest.
        assertThat(detect(noisy((1..5).flatMap { listOf(8 to 7.0, 10 to 3.0) } + listOf(120 to 3.0), seed = 8))).isNull()
    }

    private fun detect(speed: SampleSeries): List<Phase>? = SprintDetection.detect(speed, start, speed.times.last() + 1_000)

    /** 1 Hz speed from (seconds, m/s) blocks with uniform noise of +-0.3 m/s, never negative. */
    private fun noisy(blocks: List<Pair<Int, Double>>, seed: Long): SampleSeries {
        val random = Random(seed)
        val total = blocks.sumOf { it.first }
        val values = DoubleArray(total)
        var i = 0
        for ((seconds, speed) in blocks) {
            repeat(seconds) { values[i++] = maxOf(0.0, speed + (random.nextDouble() * 2 - 1) * 0.3) }
        }
        return SampleSeries(LongArray(total) { start + it * 1_000L }, values)
    }
}
