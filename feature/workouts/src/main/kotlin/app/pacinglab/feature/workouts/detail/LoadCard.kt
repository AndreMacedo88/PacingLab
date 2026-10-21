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
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.feature.workouts.R
import kotlin.math.roundToInt

/** The session's training load by the three models, the stress score first. */
@Composable
internal fun LoadCard(load: LoadScores, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = stringResource(R.string.load_title), info = stringResource(R.string.load_info))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
                Score(stringResource(R.string.load_stress), load.stressScore, MaterialTheme.typography.headlineMedium)
                Score(stringResource(R.string.load_trimp), load.trimp, MaterialTheme.typography.titleLarge)
                Score(stringResource(R.string.load_edwards), load.edwards, MaterialTheme.typography.titleLarge)
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
