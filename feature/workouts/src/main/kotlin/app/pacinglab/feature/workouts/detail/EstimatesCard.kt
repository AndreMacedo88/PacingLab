package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.feature.workouts.R

/**
 * Whether the session feeds the fitness estimates. The athlete turns it off when heart rate did not follow
 * the effort, for instance on a run spent talking; it always applies to the whole session.
 */
@Composable
internal fun EstimatesCard(used: Boolean, onUsedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            CardHeader(
                title = stringResource(R.string.session_estimates_title),
                info = stringResource(R.string.session_estimates_info),
            )
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(value = used, role = Role.Switch, onValueChange = onUsedChange),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.session_estimates_use), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(if (used) R.string.session_estimates_used else R.string.session_estimates_left_out),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = used, onCheckedChange = null)
            }
        }
    }
}
