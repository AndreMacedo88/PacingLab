package app.pacinglab.core.maps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.theme.PacingLabTheme
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.IntensityZone
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.RouteColoring
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h300dp-xxhdpi")
class RouteMapScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** A 1.2 km out-and-back warm-up with a loop at the far end. */
    private val route: Route = run {
        val points = buildList {
            for (i in 0..40) add(55.6800 + i * 0.00006 to 12.5700 + i * 0.00012)
            for (i in 0..60) {
                val angle = i / 60.0 * 2 * Math.PI
                add(55.6824 + 0.0009 * sin(angle) to 12.5748 + 0.0016 * (1 - cos(angle)))
            }
            for (i in 40 downTo 0) add(55.6800 + i * 0.00006 - 0.00002 to 12.5700 + i * 0.00012 + 0.00004)
        }
        // Out at an easy pace, the loop faster, back slower: 3, 2 and 4 s between points.
        val gaps = List(41) { 3_000L } + List(61) { 2_000L } + List(points.size - 102) { 4_000L }
        Route(
            times = LongArray(points.size) { i -> gaps.take(i).sum() },
            latitudes = DoubleArray(points.size) { points[it].first },
            longitudes = DoubleArray(points.size) { points[it].second },
            altitudes = DoubleArray(points.size) { Double.NaN },
            accuracies = DoubleArray(points.size) { Double.NaN },
        )
    }

    @Test
    fun withoutBasemap() = capture(darkTheme = false, snapshots = null)

    @Test
    fun withoutBasemapDark() = capture(darkTheme = true, snapshots = null)

    @Test
    fun withBasemap() = capture(darkTheme = false, snapshots = { frame, density, _ -> fakeBasemap(frame, density) })

    @Test
    fun zones() = capture(darkTheme = false, snapshots = { frame, density, _ -> fakeBasemap(frame, density) }, RouteColoring.ZONES_FIVE)

    @Test
    fun zonesDark() = capture(darkTheme = true, snapshots = null, RouteColoring.ZONES_FIVE)

    @Test
    fun polarizedZones() = capture(darkTheme = false, snapshots = null, RouteColoring.ZONES_THREE)

    @Test
    fun pace() = capture(darkTheme = false, snapshots = { frame, density, _ -> fakeBasemap(frame, density) }, RouteColoring.PACE)

    @Test
    fun paceDark() = capture(darkTheme = true, snapshots = null, RouteColoring.PACE)

    @Test
    fun withoutHeartRateZonesFallBackToPace() {
        val shades = RouteShades.of(listOf(route), HeartRateSeries.EMPTY, listOf(five, three), Sport.RUN, speed = false)

        assertThat(shades.available).containsExactlyInAnyOrder(RouteColoring.PACE, RouteColoring.PLAIN)
        assertThat(shades.of(RouteColoring.ZONES_FIVE)).isInstanceOf(RouteShade.Pace::class)
        assertThat(shades.of(RouteColoring.PLAIN)).isEqualTo(RouteShade.Plain)
    }

    @Test
    fun stretchesEndWhereTheNextBegins() {
        assertThat(stretches(intArrayOf(0, 0, 1, 1, 1, 2))).containsExactly(Triple(0, 2, 0), Triple(2, 5, 1))
        assertThat(stretches(intArrayOf(3, 3))).containsExactly(Triple(0, 1, 3))
        assertThat(stretches(intArrayOf(3))).isEmpty()
    }

    private fun capture(darkTheme: Boolean, snapshots: MapSnapshots?, coloring: RouteColoring = RouteColoring.PLAIN) {
        // Heart rate climbs from easy to hard over the run, a sample every 5 s.
        val end = route.times.last()
        val heartRate = HeartRateSeries(
            LongArray((end / 5_000L).toInt() + 1) { it * 5_000L },
            IntArray((end / 5_000L).toInt() + 1) {
                125 +
                    it / 2
            },
        )
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalMapSnapshots provides snapshots) {
                    Surface {
                        val shade = rememberRouteShades(listOf(route), heartRate, listOf(five, three), Sport.RUN).of(coloring)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RouteMap(listOf(route), contentDescription = "Route", shade = shade)
                            RouteLegend(shade)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    private val five = ZoneSet(
        ZoneModel.LTHR_FIVE,
        listOf(142, 150, 159, 172).let { bounds ->
            (listOf(0) + bounds).mapIndexed { i, lower -> IntensityZone(i + 1, "Z${i + 1}", lower) }
        },
    )

    private val three = ZoneSet(
        ZoneModel.POLARIZED_THREE,
        listOf(IntensityZone(1, "Z1", 0), IntensityZone(2, "Z2", 150), IntensityZone(3, "Z3", 168)),
    )

    /** Pale squares standing in for map tiles. */
    private fun fakeBasemap(frame: MapFrame, density: Float): ImageBitmap {
        val width = (frame.widthDp * density).roundToInt()
        val height = (frame.heightDp * density).roundToInt()
        val image = ImageBitmap(width, height)
        val canvas = Canvas(image)
        val light = Paint().apply { color = Color(0xFFF2F3F0) }
        val dark = Paint().apply { color = Color(0xFFE3E6E1) }
        val cell = 24 * density
        var y = 0f
        var row = 0
        while (y < height) {
            var x = 0f
            var column = 0
            while (x < width) {
                canvas.drawRect(x, y, x + cell, y + cell, if ((row + column) % 2 == 0) light else dark)
                x += cell
                column++
            }
            y += cell
            row++
        }
        return image
    }
}
