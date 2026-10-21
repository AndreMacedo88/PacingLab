package app.pacinglab.core.analytics.geo

import app.pacinglab.core.model.Route
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import org.junit.jupiter.api.Test

class GeoTest {
    private fun route(times: List<Long>, latitudes: List<Double>) = Route(
        times = times.toLongArray(),
        latitudes = latitudes.toDoubleArray(),
        longitudes = DoubleArray(times.size) { 12.5683 },
        altitudes = DoubleArray(times.size) { Double.NaN },
        accuracies = DoubleArray(times.size) { Double.NaN },
    )

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertThat(haversineMeters(55.0, 12.5683, 56.0, 12.5683)).isCloseTo(111_195.0, 1.0)
    }

    @Test
    fun `speeds come from the distance between points and skip gaps`() {
        // 0.0001 degree of latitude is 11.12 m: 2 s apart that is 5.56 m/s; the 30-s gap gives nothing.
        val route = route(listOf(0L, 2_000L, 4_000L, 34_000L, 36_000L), listOf(55.0, 55.0001, 55.0002, 55.0003, 55.0004))

        val speeds = route.speeds()

        assertThat(speeds.times.toList()).containsExactly(0L, 2_000L, 34_000L)
        speeds.values.forEach { assertThat(it).isCloseTo(5.56, 0.01) }
        assertThat(route.lengthMeters()).isCloseTo(44.48, 0.01)
    }
}
