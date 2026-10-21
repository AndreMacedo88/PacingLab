package app.pacinglab.core.maps

import app.pacinglab.core.model.Route
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class MapFrameTest {
    private fun route(vararg points: Pair<Double, Double>) = Route(
        times = LongArray(points.size) { it * 1_000L },
        latitudes = DoubleArray(points.size) { points[it].first },
        longitudes = DoubleArray(points.size) { points[it].second },
        altitudes = DoubleArray(points.size) { Double.NaN },
        accuracies = DoubleArray(points.size) { Double.NaN },
    )

    @Test
    fun `no points give no frame`() {
        assertThat(MapFrame.fit(listOf(Route.EMPTY), 300f, 200f, 20f)).isNull()
    }

    @Test
    fun `a single point sits in the middle at the closest zoom`() {
        val frame = MapFrame.fit(listOf(route(55.68 to 12.57)), 300f, 200f, 20f)!!

        assertThat(frame.zoom).isEqualTo(MapFrame.MAX_ZOOM)
        assertThat(frame.x(12.57)).isCloseTo(150f, 0.01f)
        assertThat(frame.y(55.68)).isCloseTo(100f, 0.01f)
    }

    @Test
    fun `one degree of longitude spans the world width over 360 at the frame's zoom`() {
        val frame = MapFrame(centerLatitude = 0.0, centerLongitude = 0.0, zoom = 3.0, widthDp = 300f, heightDp = 200f)

        assertThat(frame.x(1.0) - frame.x(0.0)).isCloseTo((512 * 2.0.pow(3) / 360).toFloat(), 0.001f)
    }

    @Test
    fun `north is up`() {
        val frame = MapFrame(centerLatitude = 55.0, centerLongitude = 12.0, zoom = 12.0, widthDp = 300f, heightDp = 200f)

        assertThat(frame.y(55.01) < frame.y(55.0)).isEqualTo(true)
    }

    @Test
    fun `a wide route fills the width and is centred vertically`() {
        // About 6 km east-west and 1 km north-south in Copenhagen.
        val frame = MapFrame.fit(listOf(route(55.680 to 12.50, 55.689 to 12.60)), 300f, 200f, 20f)!!

        assertThat(frame.x(12.50)).isCloseTo(20f, 0.01f)
        assertThat(frame.x(12.60)).isCloseTo(280f, 0.01f)
        assertThat((frame.y(55.680) + frame.y(55.689)) / 2).isCloseTo(100f, 0.01f)
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `every point of any route lands inside the padding`() = runTest {
        // Points within about 20 km of central Copenhagen, in 1e-4 degree steps.
        val point = Arb.int(-2_000..2_000)
        checkAll(PropTestConfig(seed = 20_261_021), Arb.list(point, 2..40), Arb.list(point, 2..40)) { dLat, dLon ->
            val n = minOf(dLat.size, dLon.size)
            val points = Array(n) { 55.68 + dLat[it] * 1e-4 to 12.57 + dLon[it] * 1e-4 }
            val frame = MapFrame.fit(listOf(route(*points)), 340f, 180f, 20f)
            assertThat(frame).isNotNull()
            var touches = false
            for ((lat, lon) in points) {
                val x = frame!!.x(lon)
                val y = frame.y(lat)
                assertThat(x).isBetween(20f - TOLERANCE, 320f + TOLERANCE)
                assertThat(y).isBetween(20f - TOLERANCE, 160f + TOLERANCE)
                if (abs(x - 20f) < TOLERANCE || abs(y - 20f) < TOLERANCE) touches = true
            }
            // The frame is as close as the padding allows unless the route is too short to zoom further.
            if (frame!!.zoom < MapFrame.MAX_ZOOM) assertThat(touches).isEqualTo(true)
        }
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
