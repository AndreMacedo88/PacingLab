package app.pacinglab.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors

/**
 * One line on the night's vitals: all within range, one outside (named), or a warning when two or more
 * are outside at once. [name] gives each measure the name the screen uses for it.
 */
@Composable
fun VitalsVerdict(vitals: Vitals, name: @Composable (NightlyMetric) -> String, modifier: Modifier = Modifier) {
    val outside = vitals.outside
    val names = outside.map { name(it.metric) }.joinToString(", ")
    val text = when {
        outside.isEmpty() -> stringResource(R.string.vitals_all_typical)
        vitals.alert -> pluralStringResource(R.plurals.vitals_alert, outside.size, outside.size, names)
        else -> stringResource(R.string.vitals_one_outside, names)
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(
                when {
                    outside.isEmpty() -> PacingLabIcons.CheckCircle
                    vitals.alert -> PacingLabIcons.Warning
                    else -> PacingLabIcons.Info
                },
            ),
            contentDescription = null,
            tint = when {
                outside.isEmpty() -> StatusColors.Good
                vitals.alert -> StatusColors.Warning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
