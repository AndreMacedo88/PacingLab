package app.pacinglab.feature.settings.physiology

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.zones.ThresholdEstimates
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.feature.settings.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Dialog for a new entry or for editing [editing]. A new entry starts from [template] (the values in
 * force today) and today's date, so a change usually means editing one number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhysiologyEditor(
    editing: PhysiologyEntry?,
    template: PhysiologyEntry?,
    today: LocalDate,
    takenDates: Set<LocalDate>,
    onDismiss: () -> Unit,
    onSave: (PhysiologyEntry) -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale) }
    var epochDay by rememberSaveable { mutableLongStateOf((editing?.effectiveFrom ?: today).toEpochDay()) }
    var maxText by rememberSaveable { mutableStateOf(template?.maxHr?.toString().orEmpty()) }
    var thresholdText by rememberSaveable { mutableStateOf(template?.thresholdHr?.toString().orEmpty()) }
    var restingText by rememberSaveable { mutableStateOf(template?.restingHr?.toString().orEmpty()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)

    val input = EditorInput(maxText, thresholdText, restingText)
    val entry = input.toEntry(date)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editing == null) R.string.physiology_editor_new else R.string.physiology_editor_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.physiology_editor_from, dateFormat.format(date)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.physiology_editor_change_date)) }
                }
                if (date in takenDates) {
                    Text(
                        text = stringResource(R.string.physiology_editor_replaces),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                NumberField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = stringResource(R.string.physiology_max_hr),
                    supporting = if (input.maxError) {
                        stringResource(R.string.physiology_error_max)
                    } else {
                        stringResource(R.string.physiology_editor_max_hint)
                    },
                    isError = input.maxError,
                )
                NumberField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = stringResource(R.string.physiology_threshold_hr),
                    supporting = when {
                        input.thresholdError -> stringResource(R.string.physiology_error_threshold, (input.max ?: MAX_FALLBACK) - 1)

                        input.max != null && !input.maxError ->
                            stringResource(R.string.physiology_editor_threshold_hint, ThresholdEstimates.thresholdFromMax(input.max))

                        else -> null
                    },
                    isError = input.thresholdError,
                )
                NumberField(
                    value = restingText,
                    onValueChange = { restingText = it },
                    label = stringResource(R.string.physiology_resting_hr),
                    supporting = stringResource(
                        if (input.restingError) R.string.physiology_error_resting else R.string.physiology_editor_resting_hint,
                    ),
                    isError = input.restingError,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { entry?.let(onSave) }, enabled = entry != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let { delete ->
                    TextButton(onClick = delete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickingDate) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            epochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        }
                        pickingDate = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String?,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        suffix = { Text(stringResource(R.string.physiology_bpm_unit)) },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The editor's text fields and what is wrong with them. Empty optional fields are fine. */
internal data class EditorInput(val maxText: String, val thresholdText: String, val restingText: String) {
    val max: Int? = maxText.toIntOrNull()
    private val threshold: Int? = thresholdText.toIntOrNull()
    private val resting: Int? = restingText.toIntOrNull()

    val maxError: Boolean = maxText.isNotEmpty() && (max == null || max !in HeartRateAnchors.MAX_HR_RANGE)

    val thresholdError: Boolean = thresholdText.isNotEmpty() &&
        (threshold == null || threshold < HeartRateAnchors.MIN_THRESHOLD_HR || (max != null && threshold >= max))

    val restingError: Boolean = restingText.isNotEmpty() &&
        (
            resting == null || resting !in HeartRateAnchors.RESTING_HR_RANGE ||
                resting >= (threshold ?: max?.let(ThresholdEstimates::thresholdFromMax) ?: 0)
            )

    /** The entry these fields describe, or null while they are incomplete or invalid. */
    fun toEntry(date: LocalDate): PhysiologyEntry? {
        val maxHr = max ?: return null
        if (maxError || thresholdError || restingError) return null
        return runCatching { PhysiologyEntry(date, maxHr, threshold, resting) }.getOrNull()
    }
}

private const val MAX_FALLBACK = 240
