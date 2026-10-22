package app.pacinglab.feature.performance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.performance.BestEfforts
import app.pacinglab.core.analytics.performance.CriticalSpeed
import app.pacinglab.core.analytics.performance.CriticalSpeedGap
import app.pacinglab.core.analytics.performance.CriticalSpeedResult
import app.pacinglab.core.analytics.performance.Effort
import app.pacinglab.core.analytics.performance.EffortSource
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Critical speed and D' when the athlete's efforts allow a fit; otherwise what is missing, with the
 * efforts found so far. Each effort opens its run.
 */
@Composable
internal fun CriticalSpeedCard(
    result: CriticalSpeedResult,
    onOpenWorkout: (String) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val fit = result.fit
    PerformanceCard(stringResource(R.string.cs_title), stringResource(R.string.cs_info), modifier) {
        if (fit != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.pace_per_km, pace(1_000 / fit.speed, 1_000.0)),
                    style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                )
                Column {
                    Text(
                        stringResource(R.string.cs_d_prime, fit.dPrime.roundToInt()),
                        style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                    )
                    Text(
                        stringResource(R.string.cs_speed, "%.1f".format(locale, fit.speed * 3.6)),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (fit.estimated) EstimateNote()
        } else {
            Text(
                text = stringResource(
                    when (result.gap) {
                        CriticalSpeedGap.NO_SHORT_EFFORT -> R.string.cs_needs_short
                        CriticalSpeedGap.INCONSISTENT -> R.string.cs_inconsistent
                        else -> R.string.cs_needs_long
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column {
            for (length in BestEfforts.SUSTAINED_DURATIONS) {
                EffortRow(
                    minutes = length / 60,
                    effort = result.efforts.firstOrNull { it.seconds.roundToInt() == length },
                    optional = length != BestEfforts.SUSTAINED_DURATIONS.first() && length != BestEfforts.SUSTAINED_DURATIONS.last(),
                    onOpenWorkout = onOpenWorkout,
                    locale = locale,
                )
            }
        }
    }
}

@Composable
private fun EffortRow(
    minutes: Int,
    effort: Effort?,
    optional: Boolean,
    onOpenWorkout: (String) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val date = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
    val clickable = if (effort != null) Modifier.clickable { onOpenWorkout(effort.workoutId) } else Modifier
    Row(
        modifier = modifier.fillMaxWidth().then(clickable).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (effort != null) PacingLabIcons.CheckCircle else PacingLabIcons.Timer),
            contentDescription = null,
            tint = if (effort != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.cs_minutes, minutes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp).width(64.dp),
        )
        if (effort != null) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.pace_per_km, pace(effort.seconds, effort.meters)),
                    style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                    fontStyle = if (effort.source == EffortSource.WATCH) FontStyle.Italic else FontStyle.Normal,
                )
                Text(
                    text = if (effort.source == EffortSource.WATCH) {
                        stringResource(R.string.cs_effort_watch, date.format(effort.date))
                    } else {
                        date.format(effort.date)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(PacingLabIcons.ChevronRight),
                contentDescription = stringResource(R.string.open_run),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = if (optional) {
                    stringResource(R.string.cs_optional)
                } else {
                    stringResource(
                        R.string.cs_missing,
                        CriticalSpeed.WINDOW_DAYS / 7,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun EstimateNote(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painterResource(PacingLabIcons.Info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.estimate_watch),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
