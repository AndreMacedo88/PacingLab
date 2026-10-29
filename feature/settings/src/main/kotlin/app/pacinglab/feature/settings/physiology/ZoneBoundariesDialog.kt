package app.pacinglab.feature.settings.physiology

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.model.ZoneBoundaries
import app.pacinglab.core.model.ZoneSet
import app.pacinglab.feature.settings.R

/**
 * Sets where each zone above the first starts, for the model of [zones], starting from the boundaries in
 * use. "Automatic" goes back to boundaries worked out from threshold heart rate.
 */
@Composable
internal fun ZoneBoundariesDialog(
    zones: ZoneSet,
    custom: Boolean,
    onSave: (List<Int>?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val starts = zones.zones.drop(1).map { it.lowerBpm }
    var texts by rememberSaveable(zones.model) { mutableStateOf(starts.map { it.toString() }) }
    val values = texts.map { it.toIntOrNull() }
    val valid = values.all { it != null && it in ZoneBoundaries.BPM_RANGE } && values.filterNotNull().zipWithNext().all { (a, b) -> b > a }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.zones_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.zones_edit_hint), style = MaterialTheme.typography.bodyMedium)
                texts.forEachIndexed { i, text ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = { changed ->
                            texts = texts.toMutableList().apply { set(i, changed.filter(Char::isDigit).take(3)) }
                        },
                        label = {
                            Text(stringResource(R.string.zones_edit_field, i + 2, stringResource(zones.model.zoneName(i + 1))))
                        },
                        suffix = { Text(stringResource(R.string.zones_bpm)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = values[i] == null || values[i] !in ZoneBoundaries.BPM_RANGE,
                    )
                }
                if (!valid) {
                    Text(
                        stringResource(R.string.zones_edit_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(values.filterNotNull()) }, enabled = valid) { Text(stringResource(R.string.zones_save)) }
        },
        dismissButton = {
            if (custom) {
                TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.zones_automatic)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.zones_cancel)) }
            }
        },
    )
}
