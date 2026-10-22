package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.session.Split
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.feature.workouts.R
import java.util.Locale

/**
 * Kilometre splits: time, pace (or speed, one tap away) and average heart rate of each kilometre, with a
 * bar for speed that starts at zero so that its length compares honestly.
 */
@Composable
internal fun SplitsCard(splits: List<Split>, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    val fastest = splits.maxOf { it.distanceMeters / it.durationMillis.coerceAtLeast(1) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val header = MaterialTheme.typography.labelMedium
    val body = MaterialTheme.typography.bodyMedium.tabularNumbers()
    val bar = chartColors().series[0]
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.splits_title), info = stringResource(R.string.splits_info))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.splits_header_km), style = header, color = muted, modifier = Modifier.weight(KM_WEIGHT))
                Text(
                    stringResource(R.string.intervals_header_time),
                    style = header,
                    color = muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(TIME_WEIGHT),
                )
                Text(
                    stringResource(if (toggle.showSpeed) R.string.intervals_header_speed else R.string.intervals_header_pace),
                    style = header,
                    color = muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(PACE_WEIGHT).speedToggle(toggle),
                )
                Box(Modifier.weight(BAR_WEIGHT))
                Text(
                    stringResource(R.string.splits_header_hr),
                    style = header,
                    color = muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(HR_WEIGHT),
                )
            }
            for (split in splits) {
                val km = if (split.distanceMeters >= 999.5) split.number.toString() else Formats.kilometres(split.distanceMeters, locale)
                val time = Formats.duration(split.durationMillis)
                val pace = paceValue(split.durationMillis, split.distanceMeters, toggle.showSpeed, locale)
                val hr = split.averageHr?.toString() ?: DASH
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = listOf(km, time, pace, hr).joinToString(", ")
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(km, style = body, modifier = Modifier.weight(KM_WEIGHT))
                    Text(time, style = body, textAlign = TextAlign.End, modifier = Modifier.weight(TIME_WEIGHT))
                    Text(pace, style = body, textAlign = TextAlign.End, modifier = Modifier.weight(PACE_WEIGHT).speedToggle(toggle))
                    Box(Modifier.weight(BAR_WEIGHT).padding(start = 12.dp, end = 8.dp)) {
                        val share = (split.distanceMeters / split.durationMillis.coerceAtLeast(1) / fastest).toFloat()
                        Box(
                            Modifier
                                .fillMaxWidth(share.coerceIn(0f, 1f))
                                .height(8.dp)
                                .background(bar, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        )
                    }
                    Text(hr, style = body, color = muted, textAlign = TextAlign.End, modifier = Modifier.weight(HR_WEIGHT))
                }
            }
        }
    }
}

private const val KM_WEIGHT = 0.6f
private const val TIME_WEIGHT = 1f
private const val PACE_WEIGHT = 0.9f
private const val BAR_WEIGHT = 1.6f
private const val HR_WEIGHT = 0.6f
