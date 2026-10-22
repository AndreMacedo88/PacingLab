package app.pacinglab.core.analytics.session

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class SplitsTest {
    /** One sample a second at [speed] m/s for [seconds]. */
    private fun steady(seconds: Int, speed: Double) = SampleSeries(LongArray(seconds) { it * 1_000L }, DoubleArray(seconds) { speed })

    private val heartRate = HeartRateSeries(LongArray(1_000) { it * 1_000L }, IntArray(1_000) { if (it < 400) 140 else 160 })

    @Test
    fun `steady running splits into whole kilometres and a last part`() {
        // 2.5 km at 4 m/s: 250 s per kilometre.
        val splits = Splits.of(Route.EMPTY, steady(626, 4.0), heartRate, recordedDistanceMeters = null)

        assertThat(splits.map { it.number }).containsExactly(1, 2, 3)
        assertThat(splits[0].durationMillis.toDouble()).isCloseTo(250_000.0, 1_000.0)
        assertThat(splits[1].durationMillis.toDouble()).isCloseTo(250_000.0, 1_000.0)
        assertThat(splits[2].distanceMeters).isCloseTo(500.0, 5.0)
        assertThat(splits[0].averageHr).isEqualTo(140)
        assertThat(splits[2].averageHr).isEqualTo(160)
    }

    @Test
    fun `distance is scaled to what the watch reported when the two are close`() {
        // The stream says 2.5 km, the watch 2.4 km: the last split shrinks to 400 m.
        val splits = Splits.of(Route.EMPTY, steady(626, 4.0), heartRate, recordedDistanceMeters = 2_400.0)

        assertThat(splits.last().distanceMeters).isCloseTo(400.0, 5.0)
        assertThat(splits.sumOf { it.distanceMeters }).isCloseTo(2_400.0, 1.0)
    }

    @Test
    fun `a GPS track gives the distance when there is one`() {
        // 0.0001 degree of latitude is 11.12 m; 300 points a second apart cover 3.3 km.
        val route = Route(
            LongArray(300) { it * 1_000L },
            DoubleArray(300) { 55.6761 + 0.0001 * it },
            DoubleArray(300) { 12.5683 },
            DoubleArray(300) { Double.NaN },
            DoubleArray(300) { Double.NaN },
        )

        val splits = Splits.of(route, SampleSeries.EMPTY, heartRate, recordedDistanceMeters = null)

        assertThat(splits.size).isEqualTo(4)
        assertThat(splits.first().durationMillis.toDouble()).isCloseTo(89_900.0, 1_000.0)
    }

    @Test
    fun `less than a kilometre gives no splits`() {
        assertThat(Splits.of(Route.EMPTY, steady(200, 4.0), heartRate, null)).isEmpty()
    }
}
