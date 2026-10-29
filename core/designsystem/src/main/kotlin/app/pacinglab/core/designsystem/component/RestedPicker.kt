package app.pacinglab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.pacinglab.core.designsystem.R

/**
 * How rested the athlete feels, one tap from 1 (exhausted) to 5 (fully rested). The words under the row
 * name both ends until a value is chosen, then the chosen one. Picking the chosen value again is
 * reported like any other pick; callers clear the rating then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestedPicker(rating: Int?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            for (value in 1..RESTED_MAX) {
                val word = restedWord(value)
                SegmentedButton(
                    selected = rating == value,
                    onClick = { onRate(value) },
                    shape = SegmentedButtonDefaults.itemShape(value - 1, RESTED_MAX),
                    icon = {},
                    modifier = Modifier.semantics { contentDescription = "$value, $word" },
                ) { Text(value.toString()) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = restedWord(rating ?: 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (rating == null) {
                Text(restedWord(RESTED_MAX), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The word for a rating from 1 (exhausted) to 5 (fully rested). */
@Composable
fun restedWord(rating: Int): String = stringResource(
    when (rating) {
        1 -> R.string.rested_1
        2 -> R.string.rested_2
        3 -> R.string.rested_3
        4 -> R.string.rested_4
        else -> R.string.rested_5
    },
)

private const val RESTED_MAX = 5
