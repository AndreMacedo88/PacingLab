package app.pacinglab.core.maps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.ZoneSet
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale
import kotlin.math.abs

/**
 * The route on a map that pans and zooms, on the same OpenFreeMap styles as the small maps. Where heart
 * rate was recorded and [zones] are known, each stretch of the route takes the colour of the heart rate
 * zone it was run in, as on the heart rate chart; otherwise the route is drawn in the theme's primary
 * colour. Start and finish are marked with dots, the start hollow.
 *
 * Runs on the native MapLibre library, so it draws nothing in JVM tests.
 */
@Composable
fun InteractiveRouteMap(routes: List<Route>, heartRate: HeartRateSeries, zones: ZoneSet?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fills = zones?.let { zoneColors(it.size) }
    val plain = MaterialTheme.colorScheme.primary
    val casing = MaterialTheme.colorScheme.surface
    val padding = PADDING_DP * LocalDensity.current.density
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { map ->
                    show(map, view, routes, heartRate, zones, fills, plain, casing, dark, padding.toInt())
                }
            }
        },
        modifier = modifier,
    )
}

private fun show(
    map: MapLibreMap,
    view: MapView,
    routes: List<Route>,
    heartRate: HeartRateSeries,
    zones: ZoneSet?,
    fills: List<Color>?,
    plain: Color,
    casing: Color,
    dark: Boolean,
    padding: Int,
) {
    map.uiSettings.setLogoEnabled(false)
    map.uiSettings.setRotateGesturesEnabled(false)
    map.uiSettings.setTiltGesturesEnabled(false)
    map.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { style ->
        style.addSource(GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(stretches(routes, heartRate, zones, fills, plain))))
        style.addLayer(
            LineLayer(CASING_LAYER, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor(hex(casing)),
                PropertyFactory.lineWidth(CASING_WIDTH),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor(Expression.get(COLOR)),
                PropertyFactory.lineWidth(LINE_WIDTH),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        val points = routes.filter { it.size > 0 }
        if (points.isNotEmpty()) {
            val first = points.first()
            val last = points.last()
            val start = Feature.fromGeometry(Point.fromLngLat(first.longitudes[0], first.latitudes[0])).apply {
                addStringProperty(COLOR, hex(casing))
            }
            val finish = Feature.fromGeometry(Point.fromLngLat(last.longitudes[last.size - 1], last.latitudes[last.size - 1]))
                .apply { addStringProperty(COLOR, hex(plain)) }
            style.addSource(GeoJsonSource(ENDS_SOURCE, FeatureCollection.fromFeatures(listOf(start, finish))))
            style.addLayer(
                CircleLayer(ENDS_LAYER, ENDS_SOURCE).withProperties(
                    PropertyFactory.circleColor(Expression.get(COLOR)),
                    PropertyFactory.circleRadius(END_RADIUS),
                    PropertyFactory.circleStrokeColor(hex(plain)),
                    PropertyFactory.circleStrokeWidth(END_STROKE),
                ),
            )
        }
        val bounds = LatLngBounds.Builder().apply {
            for (route in routes) for (i in 0 until route.size) include(LatLng(route.latitudes[i], route.longitudes[i]))
        }
        if (routes.sumOf { it.size } >= 2) view.post { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding)) }
    }
}

/**
 * The routes as lines, split wherever the heart rate zone changes; each stretch carries its colour.
 * Consecutive stretches share their joining point so that the line has no gaps.
 */
private fun stretches(routes: List<Route>, heartRate: HeartRateSeries, zones: ZoneSet?, fills: List<Color>?, plain: Color): List<Feature> {
    val features = mutableListOf<Feature>()
    for (route in routes) {
        if (route.size < 2) continue
        var start = 0
        var current = colorAt(route.times[0], heartRate, zones, fills, plain)
        for (i in 1..route.size) {
            val color = if (i < route.size) colorAt(route.times[i], heartRate, zones, fills, plain) else null
            if (i == route.size || color != current) {
                val end = minOf(i, route.size - 1)
                val coordinates = (start..end).map { Point.fromLngLat(route.longitudes[it], route.latitudes[it]) }
                if (coordinates.size >= 2) {
                    features += Feature.fromGeometry(LineString.fromLngLats(coordinates)).apply { addStringProperty(COLOR, hex(current)) }
                }
                start = end
                if (color != null) current = color
            }
        }
    }
    return features
}

/** The zone colour of the heart rate nearest to [time], within [MAX_HR_GAP_MILLIS]; [plain] without one. */
private fun colorAt(time: Long, heartRate: HeartRateSeries, zones: ZoneSet?, fills: List<Color>?, plain: Color): Color {
    if (zones == null || fills == null || heartRate.isEmpty()) return plain
    val found = heartRate.times.binarySearch(time)
    val index = if (found >= 0) {
        found
    } else {
        val after = -found - 1
        listOf(after - 1, after).filter { it in 0 until heartRate.size }.minByOrNull { abs(heartRate.times[it] - time) } ?: return plain
    }
    if (abs(heartRate.times[index] - time) > MAX_HR_GAP_MILLIS) return plain
    return fills[zones.indexOf(heartRate.bpm[index])]
}

private fun hex(color: Color): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color.toArgb())

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
private const val ROUTE_SOURCE = "route"
private const val ENDS_SOURCE = "ends"
private const val CASING_LAYER = "route-casing"
private const val ROUTE_LAYER = "route-line"
private const val ENDS_LAYER = "route-ends"
private const val COLOR = "color"
private const val LINE_WIDTH = 4.5f
private const val CASING_WIDTH = 8f
private const val END_RADIUS = 6f
private const val END_STROKE = 2.5f
private const val PADDING_DP = 48f
private const val MAX_HR_GAP_MILLIS = 15_000L
