package app.pacinglab.core.maps

import app.pacinglab.core.model.Route
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * The part of the Web Mercator world a map of [widthDp] x [heightDp] shows: its centre and zoom, as
 * MapLibre defines them (the whole world is 512 dp wide at zoom 0). The same frame positions the
 * basemap snapshot and the route drawn on top, so both line up.
 */
data class MapFrame(val centerLatitude: Double, val centerLongitude: Double, val zoom: Double, val widthDp: Float, val heightDp: Float) {
    private val worldDp = WORLD_DP * 2.0.pow(zoom)
    private val centerX = mercatorX(centerLongitude)
    private val centerY = mercatorY(centerLatitude)

    /** Horizontal position of [longitude] in dp from the left edge of the map. */
    fun x(longitude: Double): Float = ((mercatorX(longitude) - centerX) * worldDp + widthDp / 2).toFloat()

    /** Vertical position of [latitude] in dp from the top edge of the map. */
    fun y(latitude: Double): Float = ((mercatorY(latitude) - centerY) * worldDp + heightDp / 2).toFloat()

    companion object {
        private const val WORLD_DP = 512.0

        /** Closest zoom for a route that is a single point or a few metres long. */
        const val MAX_ZOOM = 16.0

        /**
         * The frame that shows every point of [routes] with at least [paddingDp] to each edge, or null when
         * there are no points.
         */
        fun fit(routes: List<Route>, widthDp: Float, heightDp: Float, paddingDp: Float): MapFrame? {
            require(widthDp > 0 && heightDp > 0) { "The map needs a size" }
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (route in routes) {
                for (i in 0 until route.size) {
                    val x = mercatorX(route.longitudes[i])
                    val y = mercatorY(route.latitudes[i])
                    minX = min(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = min(minY, y)
                    maxY = maxOf(maxY, y)
                }
            }
            if (minX > maxX) return null
            val usableWidth = (widthDp - 2 * paddingDp).coerceAtLeast(1f)
            val usableHeight = (heightDp - 2 * paddingDp).coerceAtLeast(1f)
            val zoom = min(
                log2(usableWidth / ((maxX - minX) * WORLD_DP)),
                log2(usableHeight / ((maxY - minY) * WORLD_DP)),
            ).coerceAtMost(MAX_ZOOM)
            return MapFrame(
                centerLatitude = latitudeOf((minY + maxY) / 2),
                centerLongitude = (minX + maxX) / 2 * 360 - 180,
                zoom = zoom,
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }

        /** Longitude as a fraction of the world's width, 0 at 180° W. */
        internal fun mercatorX(longitude: Double): Double = (longitude + 180) / 360

        /** Latitude as a fraction of the world's height, 0 at the top. */
        internal fun mercatorY(latitude: Double): Double = 0.5 - ln(tan(PI / 4 + Math.toRadians(latitude) / 2)) / (2 * PI)

        private fun latitudeOf(y: Double): Double = Math.toDegrees(2 * atan(exp((0.5 - y) * 2 * PI)) - PI / 2)
    }
}
