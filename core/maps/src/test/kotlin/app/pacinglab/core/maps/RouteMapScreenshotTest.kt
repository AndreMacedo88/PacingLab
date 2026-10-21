package app.pacinglab.core.maps

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
import app.pacinglab.core.model.Route
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
@Config(sdk = [Config.NEWEST_SDK], qualifiers = "w384dp-h260dp-xxhdpi")
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
        Route(
            times = LongArray(points.size) { it * 3_000L },
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

    private fun capture(darkTheme: Boolean, snapshots: MapSnapshots?) {
        compose.setContent {
            PacingLabTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalMapSnapshots provides snapshots) {
                    Surface {
                        RouteMap(listOf(route), contentDescription = "Route", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

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
