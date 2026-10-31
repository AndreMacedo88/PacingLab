package app.pacinglab.core.designsystem.component

import androidx.annotation.StringRes
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.model.RouteColoring

/** Short name of the choice, for chips and segmented buttons; [speed] names pace as speed (rides). */
@StringRes
fun RouteColoring.label(speed: Boolean = false): Int = when (this) {
    RouteColoring.ZONES_FIVE -> R.string.route_coloring_zones_five
    RouteColoring.ZONES_THREE -> R.string.route_coloring_zones_three
    RouteColoring.PACE -> if (speed) R.string.route_coloring_speed else R.string.route_coloring_pace
    RouteColoring.PLAIN -> R.string.route_coloring_plain
}
