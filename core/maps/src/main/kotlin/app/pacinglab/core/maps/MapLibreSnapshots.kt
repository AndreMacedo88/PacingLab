package app.pacinglab.core.maps

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Basemaps rendered offscreen by MapLibre from OpenFreeMap's vector tiles (no account or key needed).
 * MapLibre caches tiles on disk; finished images are kept in memory for the screens visited last.
 */
class MapLibreSnapshots(context: Context) : MapSnapshots {
    private val context = context.applicationContext
    private val cache = LruCache<Key, ImageBitmap>(CACHED_IMAGES)
    private val main = Handler(Looper.getMainLooper())

    private data class Key(val frame: MapFrame, val density: Float, val dark: Boolean)

    override suspend fun snapshot(frame: MapFrame, density: Float, dark: Boolean): ImageBitmap? {
        val key = Key(frame, density, dark)
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.Main.immediate) { render(frame, density, dark) } ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    /** Must run on the main thread, like everything MapLibre does. */
    private suspend fun render(frame: MapFrame, density: Float, dark: Boolean): Bitmap? = suspendCancellableCoroutine { continuation ->
        MapLibre.getInstance(context)
        val camera = CameraPosition.Builder()
            .target(LatLng(frame.centerLatitude, frame.centerLongitude))
            .zoom(frame.zoom)
            .build()
        // Sizes are in dp; MapLibre multiplies them by the pixel ratio.
        val options = MapSnapshotter.Options(frame.widthDp.roundToInt(), frame.heightDp.roundToInt())
            .withStyleBuilder(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE))
            .withCameraPosition(camera)
            .withPixelRatio(density)
            .withLogo(false)
            .withAttribution(false)
        val snapshotter = MapSnapshotter(context, options)
        snapshotter.start(
            { snapshot -> if (continuation.isActive) continuation.resume(snapshot.bitmap) },
            { error ->
                Log.w(TAG, "Map snapshot failed: $error")
                if (continuation.isActive) continuation.resume(null)
            },
        )
        continuation.invokeOnCancellation { main.post { snapshotter.cancel() } }
    }

    private companion object {
        const val TAG = "MapLibreSnapshots"
        const val CACHED_IMAGES = 6

        // Muted styles, so that the route stands out.
        const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/positron"
        const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
    }
}
