package app.pacinglab.feature.today

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.data.repository.DayHeartRate
import app.pacinglab.core.designsystem.chart.HeartRateChart
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.ZoneSet
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Today's heart rate: the latest value the watch passed on, with its time, the day's range and resting
 * heart rate, and the day so far coloured by zone. The watch's app hands heart rate to Health Connect
 * only when it syncs, so "latest" can be a quarter of an hour old; the time says how old.
 */
@Composable
internal fun HeartRateTodayCard(
    day: DayHeartRate?,
    zones: ZoneSet?,
    restingHr: Double?,
    locale: Locale,
    zoneId: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val latest = day?.latest
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.heart_rate_title), info = stringResource(R.string.heart_rate_info))
            if (latest == null) {
                Text(
                    stringResource(R.string.heart_rate_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.unit_bpm, latest.second),
                    style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.heart_rate_latest, time.format(latest.first.atZone(zoneId))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(
                            stringResource(R.string.heart_rate_range, day.lowest ?: 0, day.highest ?: 0),
                            restingHr?.let { stringResource(R.string.heart_rate_resting, it.roundToInt()) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (zones != null && day.series.size > 1) {
                HeartRateChart(
                    heartRate = day.series,
                    zones = zones,
                    zoneId = zoneId,
                    description = stringResource(R.string.heart_rate_chart, day.lowest ?: 0, day.highest ?: 0),
                    height = 96.dp,
                    scrubbable = false,
                    showLegend = false,
                )
            }
        }
    }
}
