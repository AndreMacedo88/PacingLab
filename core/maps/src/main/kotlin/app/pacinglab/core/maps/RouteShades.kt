package app.pacinglab.core.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.geo.PaceShading
import app.pacinglab.core.analytics.geo.RouteShading
import app.pacinglab.core.analytics.workout.StreamCleaning
import app.pacinglab.core.designsystem.chart.ZoneLegend
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.paceColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.RouteColoring
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet

/** How a map draws its route: in one colour, or each point's level in a scale of colours. */
@Immutable
sealed interface RouteShade {
    data object Plain : RouteShade

    /** Heart rate zones of [zones], one level per route point. */
    class Zones(val zones: ZoneSet, val levels: List<IntArray>) : RouteShade

    /** Pace bands; [speed] labels the scale in km/h rather than per kilometre. */
    class Pace(val shading: PaceShading, val speed: Boolean) : RouteShade
}

/** Every way a session's routes can be coloured, worked out once so that switching between them is instant. */
@Immutable
class RouteShades(private val shades: Map<RouteColoring, RouteShade>) {
    /** The choices this session can show; a plain line always. */
    val available: Set<RouteColoring> get() = shades.keys + RouteColoring.PLAIN

    /** [coloring], or the nearest choice the session has (see [RouteColoring.within]). */
    fun of(coloring: RouteColoring): RouteShade = shades[coloring.within(available)] ?: RouteShade.Plain

    companion object {
        val NONE = RouteShades(emptyMap())

        /**
         * Zones of the five- and three-zone models in [zoneSets] where there is heart rate, and pace where
         * the routes move; [speed] labels pace in km/h.
         */
        fun of(routes: List<Route>, heartRate: HeartRateSeries, zoneSets: List<ZoneSet>, sport: Sport, speed: Boolean): RouteShades {
            val shades = mutableMapOf<RouteColoring, RouteShade>()
            if (!heartRate.isEmpty()) {
                for ((coloring, model) in listOf(
                    RouteColoring.ZONES_FIVE to ZoneModel.LTHR_FIVE,
                    RouteColoring.ZONES_THREE to ZoneModel.POLARIZED_THREE,
                )) {
                    val zones = zoneSets.firstOrNull { it.model == model } ?: continue
                    val levels = RouteShading.zones(routes, heartRate, zones)
                    if (levels.any { route -> route.any { it != RouteShading.NONE } }) shades[coloring] = RouteShade.Zones(zones, levels)
                }
            }
            RouteShading.pace(routes, StreamCleaning.maxStepSpeed(sport))?.let { shades[RouteColoring.PACE] = RouteShade.Pace(it, speed) }
            return RouteShades(shades)
        }
    }
}

/** [RouteShades.of], kept while its inputs stay the same. Rides show speed in km/h whatever [speed] says. */
@Composable
fun rememberRouteShades(
    routes: List<Route>,
    heartRate: HeartRateSeries,
    zoneSets: List<ZoneSet>,
    sport: Sport,
    speed: Boolean = false,
): RouteShades = remember(routes, heartRate, zoneSets, sport, speed) {
    RouteShades.of(routes, heartRate, zoneSets, sport, speed || sport == Sport.RIDE)
}

/** The colour of each level of [shade], slowest or easiest first; empty for a plain line. */
@Composable
internal fun levelColors(shade: RouteShade): List<Color> = when (shade) {
    RouteShade.Plain -> emptyList()
    is RouteShade.Zones -> zoneColors(shade.zones.size)
    is RouteShade.Pace -> paceColors()
}

/** The levels of each route in [shade], or null for a plain line. */
internal fun levelsOf(shade: RouteShade): List<IntArray>? = when (shade) {
    RouteShade.Plain -> null
    is RouteShade.Zones -> shade.levels
    is RouteShade.Pace -> shade.shading.levels
}

/**
 * What the colours of a route mean: the zones with their names, or the pace scale with its slow end on the
 * left and its fast end on the right. Nothing for a plain line.
 */
@Composable
fun RouteLegend(shade: RouteShade, modifier: Modifier = Modifier) {
    when (shade) {
        RouteShade.Plain -> Unit
        is RouteShade.Zones -> ZoneLegend(shade.zones, modifier)
        is RouteShade.Pace -> PaceLegend(shade, modifier)
    }
}

@Composable
private fun PaceLegend(shade: RouteShade.Pace, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = paceColors()
    fun label(metersPerSecond: Double): String {
        // A kilometre at this speed, so that the shared formats give pace and km/h alike.
        val millis = (KILOMETRE / metersPerSecond * MILLIS_PER_SECOND).toLong()
        return (if (shade.speed) Formats.speedKmh(millis, KILOMETRE, locale) else Formats.pacePerKm(millis, KILOMETRE)).orEmpty()
    }
    val slowest = label(shade.shading.slowest)
    val fastest = stringResource(if (shade.speed) R.string.route_legend_speed else R.string.route_legend_pace, label(shade.shading.fastest))
    val description = stringResource(R.string.route_legend_description, slowest, fastest)
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            slowest,
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.weight(1f).height(10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            colors.forEachIndexed { i, color ->
                val shape = when (i) {
                    0 -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
                    colors.lastIndex -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Box(Modifier.weight(1f).height(10.dp).background(color, shape))
            }
        }
        Text(
            fastest,
            style = MaterialTheme.typography.labelMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val KILOMETRE = 1_000.0
private const val MILLIS_PER_SECOND = 1_000.0
