package app.pacinglab.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import app.pacinglab.core.designsystem.theme.tabularNumbers

/**
 * How hard a session felt, one tap from 1 (very, very easy) to 10 (maximal): Borg's CR-10 scale as Foster
 * uses it for session RPE. The word under the row names the chosen value. Picking the chosen value again
 * is reported like any other pick; callers clear the rating then.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffortPicker(rating: Int?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            for (value in 1..EFFORT_MAX) {
                val word = effortWord(value)
                SegmentedButton(
                    selected = rating == value,
                    onClick = { onRate(value) },
                    shape = SegmentedButtonDefaults.itemShape(value - 1, EFFORT_MAX),
                    icon = {},
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.semantics { contentDescription = "$value, $word" },
                ) { Text(value.toString(), style = MaterialTheme.typography.labelLarge.tabularNumbers()) }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = effortWord(rating ?: 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (rating == null) {
                Text(effortWord(EFFORT_MAX), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The CR-10 word for a rating from 1 to 10. */
@Composable
fun effortWord(rating: Int): String = stringResource(
    when (rating) {
        1 -> R.string.effort_1
        2 -> R.string.effort_2
        3 -> R.string.effort_3
        4 -> R.string.effort_4
        5 -> R.string.effort_5
        6 -> R.string.effort_6
        7 -> R.string.effort_7
        8 -> R.string.effort_8
        9 -> R.string.effort_9
        else -> R.string.effort_10
    },
)

private const val EFFORT_MAX = 10
