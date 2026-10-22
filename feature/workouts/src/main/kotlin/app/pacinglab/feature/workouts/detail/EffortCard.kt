package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.EffortLoad
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.EffortPicker
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.feature.workouts.R
import kotlin.math.roundToInt

/**
 * How hard a gym session felt, and the load that follows from it: session RPE and the stress score that
 * counts towards fitness and fatigue in place of heart rate.
 */
@Composable
internal fun EffortCard(rating: Int?, durationMillis: Long, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = stringResource(R.string.effort_title), info = stringResource(R.string.effort_info))
            EffortPicker(rating, onRate)
            if (rating != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                    Score(
                        stringResource(R.string.effort_session_rpe),
                        EffortLoad.sessionRpe(rating, durationMillis),
                        MaterialTheme.typography.headlineMedium,
                    )
                    Score(
                        stringResource(R.string.load_stress),
                        EffortLoad.stressScore(rating, durationMillis),
                        MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun Score(label: String, value: Double, style: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value.roundToInt().toString(), style = style.tabularNumbers())
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
