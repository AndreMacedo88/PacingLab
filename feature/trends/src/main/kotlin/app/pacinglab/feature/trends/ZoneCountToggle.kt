package app.pacinglab.feature.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * A small switch between five zones and three, for a card where the choice is a detail rather than the
 * point: legend-sized text, outlined, the chosen side filled. A tap anywhere on it flips the choice, so
 * its touch area can be full size while it looks small.
 */
@Composable
internal fun ZoneCountToggle(five: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.weeks_zones)
    val state = stringResource(if (five) R.string.weeks_zones_five else R.string.weeks_zones_three)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = five, role = Role.Switch, onValueChange = onChange)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = state
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val shape = RoundedCornerShape(50)
        Row(Modifier.height(22.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
            Side("5", chosen = five)
            Side("3", chosen = !five)
        }
    }
}

@Composable
private fun Side(text: String, chosen: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(26.dp)
            .background(if (chosen) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (chosen) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
