package app.pacinglab.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.model.RangeStanding

/**
 * A small rounded label for a state, such as "In range" under a metric: tinted in the state's colour,
 * with an icon and a word so that the colour never carries the meaning alone. The text stays in the
 * theme's ink for contrast; the tint is light enough for it in both themes.
 */
@Composable
fun StatusPill(text: String, color: Color, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = TINT_ALPHA), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/**
 * A night's value against the athlete's usual range: "In range" in the good green, above or below in
 * the out-of-range orange with an arrow, "Learning your range" while there are too few nights.
 */
@Composable
fun RangePill(standing: RangeStanding?, modifier: Modifier = Modifier) {
    when (standing) {
        null -> StatusPill(stringResource(R.string.status_learning), MaterialTheme.colorScheme.outline, PacingLabIcons.Schedule, modifier)

        RangeStanding.IN_RANGE -> StatusPill(
            stringResource(R.string.status_in_range),
            StatusColors.Good,
            PacingLabIcons.CheckCircle,
            modifier,
        )

        RangeStanding.ABOVE -> StatusPill(stringResource(R.string.status_above), chartColors().outOfRange, PacingLabIcons.ArrowUp, modifier)

        RangeStanding.BELOW -> StatusPill(
            stringResource(R.string.status_below),
            chartColors().outOfRange,
            PacingLabIcons.ArrowDown,
            modifier,
        )
    }
}

private const val TINT_ALPHA = 0.22f
