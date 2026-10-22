package app.pacinglab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.component.CardHeader

/**
 * "How rested do you feel?" on a scale of 1 to 5, answered once in the morning: the part of sleep health
 * no watch can measure. Picking the chosen answer again clears it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RestedCard(rating: Int?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.rested_title), info = stringResource(R.string.rested_info))
            val words = restedWords()
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                for (value in 1..RATINGS) {
                    SegmentedButton(
                        selected = rating == value,
                        onClick = { onRate(value) },
                        shape = SegmentedButtonDefaults.itemShape(value - 1, RATINGS),
                        icon = {},
                        modifier = Modifier.semantics { contentDescription = "$value, ${words[value - 1]}" },
                    ) { Text(value.toString()) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = rating?.let { words[it - 1] } ?: words.first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (rating == null) {
                    Text(words.last(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The words for ratings 1 to 5, from exhausted to fully rested. */
@Composable
internal fun restedWords(): List<String> = listOf(
    stringResource(R.string.rested_1),
    stringResource(R.string.rested_2),
    stringResource(R.string.rested_3),
    stringResource(R.string.rested_4),
    stringResource(R.string.rested_5),
)

private const val RATINGS = 5
