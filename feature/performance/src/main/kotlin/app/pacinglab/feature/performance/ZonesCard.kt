package app.pacinglab.feature.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.component.zoneNumber
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.ZoneSet

/** Where a heart rate value in use comes from. */
enum class ValueSource { ATHLETE, MAX_ESTIMATE, THRESHOLD_ESTIMATE, SHARE_OF_MAX, NIGHTS }

/**
 * The heart rate values in force today, with where each comes from, and the five zones they give.
 *
 * @property restingSource null without a resting heart rate.
 */
data class HeartRateValues(
    val anchors: HeartRateAnchors,
    val zones: ZoneSet,
    val maxSource: ValueSource,
    val thresholdSource: ValueSource,
    val restingSource: ValueSource?,
)

/**
 * A summary of Settings > Heart rate and zones, which a tap opens: maximal, threshold and resting heart rate with
 * their sources, and the five zones as a strip in their colours with each zone's range.
 */
@Composable
internal fun ZonesCard(values: HeartRateValues?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    PerformanceCard(
        title = stringResource(R.string.performance_zones_title),
        info = stringResource(R.string.performance_zones_info),
        modifier = modifier,
        onClick = onOpen,
    ) {
        if (values == null) {
            Text(
                stringResource(R.string.performance_zones_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@PerformanceCard
        }
        val anchors = values.anchors
        ValueRow(stringResource(R.string.performance_zones_max), values.maxSource, anchors.maxHr)
        ValueRow(stringResource(R.string.performance_zones_threshold), values.thresholdSource, anchors.thresholdHr)
        val resting = anchors.restingHr
        if (resting != null &&
            values.restingSource != null
        ) {
            ValueRow(stringResource(R.string.performance_zones_resting), values.restingSource, resting)
        }
        ZoneStrip(values.zones)
    }
}

@Composable
private fun ValueRow(name: String, source: ValueSource, bpm: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(
                    when (source) {
                        ValueSource.ATHLETE -> R.string.performance_zones_yours
                        ValueSource.MAX_ESTIMATE -> R.string.performance_zones_max_estimate
                        ValueSource.THRESHOLD_ESTIMATE -> R.string.performance_zones_threshold_estimate
                        ValueSource.SHARE_OF_MAX -> R.string.performance_zones_share
                        ValueSource.NIGHTS -> R.string.performance_zones_nights
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.performance_zones_bpm, bpm), style = MaterialTheme.typography.titleMedium.tabularNumbers())
    }
}

/** The zones side by side in their colours, each with its number and range in bpm. */
@Composable
private fun ZoneStrip(zones: ZoneSet) {
    val colors = zoneColors(zones.zones.size)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        zones.zones.forEachIndexed { i, zone ->
            val next = zones.zones.getOrNull(i + 1)
            val range = when {
                i == 0 && next != null -> "<${next.lowerBpm}"
                next == null -> "≥${zone.lowerBpm}"
                else -> "${zone.lowerBpm}–${next.lowerBpm - 1}"
            }
            val name = stringResource(zones.model.zoneName(i))
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = "$name $range" },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(colors[i], RoundedCornerShape(2.dp)))
                Text(
                    zoneNumber(i),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    range,
                    style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
