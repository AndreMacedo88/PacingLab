package app.pacinglab.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet

/** Display name of the zone at [index] (0-based) in this model. */
@StringRes
fun ZoneModel.zoneName(index: Int): Int = when (this) {
    ZoneModel.LTHR_FIVE -> LTHR_NAMES
    ZoneModel.HEART_RATE_RESERVE_FIVE -> HRR_NAMES
    ZoneModel.POLARIZED_THREE -> POLARIZED_NAMES
}[index]

/** The zone's heart rate span, such as "150–158 bpm". */
@Composable
fun zoneRange(zones: ZoneSet, index: Int): String {
    val lower = zones.zones[index].lowerBpm
    val upper = zones.upperBpm(index)
    return when {
        index == 0 && upper != null -> stringResource(R.string.zone_range_below, upper)
        upper == null -> stringResource(R.string.zone_range_from, lower)
        else -> stringResource(R.string.zone_range_between, lower, upper - 1)
    }
}

/** "Z3" */
@Composable
fun zoneNumber(index: Int): String = stringResource(R.string.zone_number, index + 1)

/** A small square in the zone's colour, shown beside the zone's name so colour never stands alone. */
@Composable
fun ZoneSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
}

private val LTHR_NAMES = listOf(
    R.string.zone_lthr_recovery,
    R.string.zone_lthr_aerobic,
    R.string.zone_lthr_tempo,
    R.string.zone_lthr_threshold,
    R.string.zone_lthr_vo2max,
)

private val HRR_NAMES = listOf(
    R.string.zone_hrr_recovery,
    R.string.zone_hrr_aerobic,
    R.string.zone_hrr_tempo,
    R.string.zone_hrr_threshold,
    R.string.zone_hrr_vo2max,
)

private val POLARIZED_NAMES = listOf(R.string.zone_polarized_low, R.string.zone_polarized_middle, R.string.zone_polarized_high)
