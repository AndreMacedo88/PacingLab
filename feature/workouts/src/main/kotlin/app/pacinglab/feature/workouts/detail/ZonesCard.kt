package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.component.zoneNumber
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.feature.workouts.R
import kotlin.math.roundToInt

/**
 * Time in each heart rate zone as horizontal bars, hardest zone on top. Bars are scaled to the largest
 * zone so that differences stay visible; the time and share beside each bar carry the exact values.
 *
 * @param distributions five-zone model first, polarized model second; empty when zones are not set up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZonesCard(distributions: List<ZoneDistribution>, onOpenPhysiology: () -> Unit, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(title = stringResource(R.string.zones_title), info = stringResource(R.string.zones_info))
            when {
                distributions.isEmpty() -> {
                    Text(stringResource(R.string.zones_missing), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenPhysiology) { Text(stringResource(R.string.zones_set_up)) }
                }

                distributions.first().totalMillis == 0L -> Text(
                    text = stringResource(R.string.zones_no_heart_rate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(R.string.zones_five, R.string.zones_three).forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = selected == index,
                                onClick = { selected = index },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    ZoneBars(distributions[selected.coerceAtMost(distributions.lastIndex)])
                }
            }
        }
    }
}

@Composable
private fun ZoneBars(distribution: ZoneDistribution, modifier: Modifier = Modifier) {
    val zones = distribution.zoneSet
    val colors = zoneColors(zones.size)
    val longest = distribution.millis.max().coerceAtLeast(1)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (index in zones.zones.indices.reversed()) {
            val millis = distribution.millis[index]
            val number = zoneNumber(index)
            val name = stringResource(zones.model.zoneName(index))
            val time = Formats.duration(millis)
            val share = stringResource(R.string.zones_share, (distribution.fraction(index) * 100).roundToInt())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$number $name: $time, $share" },
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )
                Text(text = name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.width(92.dp))
                Box(Modifier.weight(1f).height(BAR_HEIGHT)) {
                    if (millis > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(millis.toFloat() / longest)
                                .height(BAR_HEIGHT)
                                .background(colors[index], RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        )
                    }
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp),
                )
                Text(
                    text = share,
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

private val BAR_HEIGHT = 12.dp
