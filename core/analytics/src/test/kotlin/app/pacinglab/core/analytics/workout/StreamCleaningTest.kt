package app.pacinglab.core.analytics.workout

import app.pacinglab.core.analytics.geo.lengthMeters
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import org.junit.jupiter.api.Test

class StreamCleaningTest {
    /** A route due north from Copenhagen City Hall at [speed] m/s, one point a second. */
    private fun route(seconds: Int, speed: Double, offsets: Map<Int, Double> = emptyMap()): Route {
        val degreesPerMeter = 1 / 111_195.0
        return Route(
            LongArray(seconds) { it * 1_000L },
            DoubleArray(seconds) { 55.6761 + (it * speed + (offsets[it] ?: 0.0)) * degreesPerMeter },
            DoubleArray(seconds) { 12.5683 },
            DoubleArray(seconds) { Double.NaN },
            DoubleArray(seconds) { Double.NaN },
        )
    }

    @Test
    fun `a point that jumps away and back leaves the track`() {
        val jumped = route(60, 3.0, offsets = mapOf(30 to 80.0))

        val cleaned = StreamCleaning.route(jumped, StreamCleaning.maxStepSpeed(Sport.RUN))

        assertThat(cleaned.size).isEqualTo(59)
        assertThat(cleaned.lengthMeters()).isCloseTo(59 * 3.0, 0.5)
    }

    @Test
    fun `a clean track is returned unchanged`() {
        val track = route(60, 3.0)

        assertThat(StreamCleaning.route(track, 9.0)).isSameInstanceAs(track)
    }

    @Test
    fun `steps faster than a runner can move count as a pause`() {
        // Running at 3 m/s, then 20 s in a car at 15 m/s.
        val track = route(40, 3.0, offsets = (20 until 40).associateWith { (it - 19) * 12.0 })

        val speeds = StreamCleaning.movingSpeeds(track, 9.0)

        assertThat(speeds.values.max()).isCloseTo(3.0, 0.01)
        assertThat(speeds.size).isEqualTo(19)
    }

    @Test
    fun `lone speed spikes are flattened but a three-second sprint survives`() {
        val values = doubleArrayOf(3.5, 3.6, 51.2, 3.5, 3.4, 3.5, 1.5, 1.5, 8.0, 8.2, 8.1, 1.5, 1.5, 9.5, 1.5, 1.5)
        val speed = SampleSeries(LongArray(values.size) { it * 1_000L }, values)

        val cleaned = StreamCleaning.speed(speed, maxSpeed = 11.0)

        // 51.2 is dropped as implausible; the lone 9.5 becomes its neighbourhood's median.
        assertThat(cleaned.values.toList()).containsExactly(3.5, 3.6, 3.5, 3.4, 3.5, 1.5, 1.5, 8.0, 8.2, 8.1, 1.5, 1.5, 1.5, 1.5, 1.5)
    }

    @Test
    fun `heart rate outside 30 to 230 and lone spikes go, steady changes stay`() {
        val bpm = intArrayOf(150, 151, 150, 152, 151, 150, 190, 151, 150, 152, 151, 150, 250, 151, 160, 165, 170)
        val series = HeartRateSeries(LongArray(bpm.size) { it * 1_000L }, bpm)

        val cleaned = StreamCleaning.heartRate(series)

        assertThat(cleaned.bpm.toList()).containsExactly(150, 151, 150, 152, 151, 150, 151, 151, 150, 152, 151, 150, 151, 160, 165, 170)
    }
}
