package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.AerobicDecoupling
import app.pacinglab.core.analytics.performance.Decoupling
import app.pacinglab.core.analytics.performance.RunHalf
import app.pacinglab.core.analytics.weather.HeatStressLevel
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.feature.workouts.R
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Pace to heart rate decoupling of a steady long run: the percentage with its verdict, and each half's pace and heart rate. */
@Composable
internal fun DecouplingCard(
    decoupling: Decoupling,
    toggle: SpeedToggle,
    locale: Locale,
    modifier: Modifier = Modifier,
    heat: HeatStressLevel? = null,
) {
    val good = decoupling.percent < AerobicDecoupling.WELL_COUPLED
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.decoupling_title), info = stringResource(R.string.decoupling_info))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.decoupling_value, "%.1f".format(locale, decoupling.percent)),
                    style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painterResource(if (good) PacingLabIcons.CheckCircle else PacingLabIcons.Warning),
                        contentDescription = null,
                        tint = if (good) MaterialTheme.colorScheme.primary else chartColors().outOfRange,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(if (good) R.string.decoupling_good else R.string.decoupling_drift),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Half(stringResource(R.string.decoupling_first_half), decoupling.firstHalf, toggle, locale)
            Half(stringResource(R.string.decoupling_second_half), decoupling.secondHalf, toggle, locale)
            if (heat != null && heat != HeatStressLevel.LOW) {
                Text(stringResource(R.string.decoupling_heat), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                pluralStringResource(R.plurals.decoupling_minutes, decoupling.minutes, decoupling.minutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Half(label: String, half: RunHalf, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = paceText((1_000_000 / half.speed).roundToLong(), 1_000.0, toggle.showSpeed, locale).orEmpty(),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            modifier = Modifier.speedToggle(toggle).padding(horizontal = 8.dp),
        )
        Text(
            text = stringResource(R.string.unit_bpm, half.bpm.roundToInt()),
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
