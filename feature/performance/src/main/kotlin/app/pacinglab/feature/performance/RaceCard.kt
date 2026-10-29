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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.MarathonShape
import app.pacinglab.core.analytics.performance.RacePrediction
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Predicted race times: one row per distance with the time from VO2max, tempered by the marathon shape
 * where the training is short of what the distance asks for (with the untempered time beside it), and
 * the time from critical speed underneath where that model applies.
 */
@Composable
internal fun RaceCard(
    predictions: List<RacePrediction>,
    vo2max: Double?,
    locale: Locale,
    modifier: Modifier = Modifier,
    shape: MarathonShape? = null,
) {
    PerformanceCard(stringResource(R.string.race_title), stringResource(R.string.race_info), modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            predictions.forEachIndexed { i, prediction ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RaceRow(prediction)
            }
        }
        shape?.let {
            Text(
                text = stringResource(
                    R.string.race_shape,
                    it.percent.roundToInt(),
                    it.weeklyKm.roundToInt(),
                    it.targetWeeklyKm.roundToInt(),
                    pluralStringResource(R.plurals.race_long_runs, it.longRuns, it.longRuns, it.longRunKm.roundToInt()),
                ),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        vo2max?.let {
            Text(
                text = stringResource(R.string.race_from_vo2max, "%.1f".format(locale, it)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RaceRow(prediction: RacePrediction, modifier: Modifier = Modifier) {
    val main = prediction.fromVo2Max ?: prediction.fromCriticalSpeed
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(prediction.distance.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = main?.let(::raceTime) ?: "–",
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            )
            Text(
                text = main?.let { stringResource(R.string.pace_per_km, pace(it, prediction.distance.meters)) }.orEmpty(),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.7f),
            )
        }
        val potential = prediction.potential
        if (prediction.fromVo2Max != null && potential != null && prediction.fromVo2Max!! - potential >= SHAPE_SHOWN_FROM_SECONDS) {
            Text(
                text = stringResource(R.string.race_potential, raceTime(potential)),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (prediction.fromVo2Max != null && prediction.fromCriticalSpeed != null) {
            Text(
                text = stringResource(R.string.race_from_critical_speed, raceTime(prediction.fromCriticalSpeed!!)),
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shape corrections smaller than this are not worth a second line. */
private const val SHAPE_SHOWN_FROM_SECONDS = 5.0
