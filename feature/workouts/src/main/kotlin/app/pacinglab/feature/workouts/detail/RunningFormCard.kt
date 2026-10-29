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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.feature.workouts.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cadence and stride length of a run from the watch's step counts: the medians over the running minutes,
 * the reps on their own where there are reps, and cadence minute by minute.
 */
@Composable
internal fun RunningFormCard(form: RunningFormView, locale: Locale, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.running_form_title), info = stringResource(R.string.running_form_info))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Figure(
                    stringResource(R.string.running_form_cadence),
                    stringResource(R.string.running_form_cadence_value, form.overall.cadence.roundToInt()),
                )
                Figure(
                    stringResource(R.string.running_form_stride),
                    stringResource(R.string.running_form_stride_value, "%.2f".format(locale, form.overall.strideMeters)),
                )
            }
            form.reps?.let { reps ->
                Text(
                    stringResource(R.string.running_form_reps, reps.cadence.roundToInt(), "%.2f".format(locale, reps.strideMeters)),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                )
            }
            if (form.minutes.size > 1) {
                Text(
                    stringResource(R.string.running_form_by_minute),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Sparkline(values = form.minutes.map { it.cadence }, color = chartColors().series[0])
            }
            Text(
                pluralStringResource(R.plurals.running_form_minutes, form.overall.minutes, form.overall.minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall.tabularNumbers())
    }
}
