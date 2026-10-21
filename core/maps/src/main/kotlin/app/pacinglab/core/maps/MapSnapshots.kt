package app.pacinglab.core.maps

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** Renders basemap images for route maps. */
fun interface MapSnapshots {
    /**
     * The basemap of [frame] at [density] pixels per dp, or null when none can be made, for example
     * while the phone is offline.
     */
    suspend fun snapshot(frame: MapFrame, density: Float, dark: Boolean): ImageBitmap?
}

/**
 * The basemap renderer route maps use. The app provides MapLibre; without one (tests, previews) routes
 * are drawn on a plain background.
 */
val LocalMapSnapshots = staticCompositionLocalOf<MapSnapshots?> { null }
