package app.pacinglab.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.TrainingPaces
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale

/** Daniels' five training paces from today's VO2max, each with what it is for. */
@Composable
internal fun TrainingPacesCard(paces: TrainingPaces, locale: Locale, modifier: Modifier = Modifier) {
    PerformanceCard(stringResource(R.string.paces_title), stringResource(R.string.paces_info), modifier) {
        val rows = listOf(
            Triple(R.string.paces_easy, R.string.paces_easy_use, paceRange(paces.easy.start, paces.easy.endInclusive)),
            Triple(R.string.paces_marathon, R.string.paces_marathon_use, paceText(paces.marathon)),
            Triple(R.string.paces_threshold, R.string.paces_threshold_use, paceText(paces.threshold)),
            Triple(R.string.paces_interval, R.string.paces_interval_use, paceText(paces.interval)),
            Triple(R.string.paces_repetition, R.string.paces_repetition_use, paceText(paces.repetition)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { i, (name, use, pace) ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(name), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(use),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(stringResource(R.string.pace_per_km, pace), style = MaterialTheme.typography.titleMedium.tabularNumbers())
                }
            }
        }
        Text(
            text = stringResource(R.string.race_from_vo2max, "%.1f".format(locale, paces.vo2max)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A pace range from the fast end to the slow end, as running watches show it. */
private fun paceRange(slow: Double, fast: Double): String = "${paceText(fast)}–${paceText(slow)}"
