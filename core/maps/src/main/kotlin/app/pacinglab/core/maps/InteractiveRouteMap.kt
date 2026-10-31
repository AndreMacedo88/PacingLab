package app.pacinglab.core.maps

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import app.pacinglab.core.model.Route
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

/**
 * The route on a map that pans and zooms, on the same OpenFreeMap styles as the small maps. Each stretch
 * takes the colour of its level in [shade] (heart rate zone or pace), stretches without one are grey, and
 * a plain [shade] draws the route in the theme's primary colour. A new [shade] recolours the route in
 * place, keeping the camera where it is. The start is marked with a filled dot and the finish with a ring.
 *
 * Runs on the native MapLibre library, so it draws nothing in JVM tests.
 */
@Composable
fun InteractiveRouteMap(routes: List<Route>, shade: RouteShade, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val plain = MaterialTheme.colorScheme.primary
    val unknown = MaterialTheme.colorScheme.outline
    val casing = MaterialTheme.colorScheme.surface
    val padding = PADDING_DP * LocalDensity.current.density
    val colors = levelColors(shade)
    val features = remember(routes, shade, colors, plain, unknown) { lines(routes, levelsOf(shade), colors, plain, unknown) }
    val route = remember { RouteSource() }
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
    LaunchedEffect(features) { route.show(features) }
    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { map -> show(map, view, routes, route, plain, casing, dark, padding.toInt()) }
            }
        },
        modifier = modifier,
    )
}

/** The route's GeoJSON source once the style has loaded, and the lines it should hold. */
private class RouteSource {
    private var lines: List<Feature> = emptyList()
    private var source: GeoJsonSource? = null

    fun show(lines: List<Feature>) {
        this.lines = lines
        source?.setGeoJson(FeatureCollection.fromFeatures(lines))
    }

    fun attach(): GeoJsonSource = GeoJsonSource(ROUTE_SOURCE, FeatureCollection.fromFeatures(lines)).also { source = it }
}

private fun show(
    map: MapLibreMap,
    view: MapView,
    routes: List<Route>,
    route: RouteSource,
    plain: Color,
    casing: Color,
    dark: Boolean,
    padding: Int,
) {
    map.uiSettings.setLogoEnabled(false)
    map.uiSettings.setRotateGesturesEnabled(false)
    map.uiSettings.setTiltGesturesEnabled(false)
    map.setStyle(Style.Builder().fromUri(if (dark) DARK_STYLE else LIGHT_STYLE)) { style ->
        style.addSource(route.attach())
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
            // As on the small maps: the start a filled dot, the finish a ring.
            val start = Feature.fromGeometry(Point.fromLngLat(first.longitudes[0], first.latitudes[0])).apply {
                addStringProperty(COLOR, hex(plain))
            }
            val finish = Feature.fromGeometry(Point.fromLngLat(last.longitudes[last.size - 1], last.latitudes[last.size - 1]))
                .apply { addStringProperty(COLOR, hex(casing)) }
            style.addSource(GeoJsonSource(ENDS_SOURCE, FeatureCollection.fromFeatures(listOf(finish, start))))
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
            for (r in routes) for (i in 0 until r.size) include(LatLng(r.latitudes[i], r.longitudes[i]))
        }
        if (routes.sumOf { it.size } >= 2) view.post { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), padding)) }
    }
}

/**
 * The routes as lines, one per run of points at the same level, each carrying its colour: the level's
 * colour, [unknown] for points without a level, and [plain] for every line when there are no [levels].
 */
private fun lines(routes: List<Route>, levels: List<IntArray>?, colors: List<Color>, plain: Color, unknown: Color): List<Feature> =
    routes.withIndex().filter { it.value.size >= 2 }.flatMap { (index, route) ->
        val runs = levels?.let { stretches(it[index]) } ?: listOf(Triple(0, route.size - 1, -1))
        runs.map { (from, to, level) ->
            val color = if (levels == null) plain else colors.getOrNull(level) ?: unknown
            val coordinates = (from..to).map { Point.fromLngLat(route.longitudes[it], route.latitudes[it]) }
            Feature.fromGeometry(LineString.fromLngLats(coordinates)).apply { addStringProperty(COLOR, hex(color)) }
        }
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
