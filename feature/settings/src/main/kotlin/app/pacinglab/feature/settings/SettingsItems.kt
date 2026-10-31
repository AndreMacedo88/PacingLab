package app.pacinglab.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import kotlin.math.roundToInt

/**
 * A setting's tile: the colour and shape of the cards on the other screens, so that settings read as
 * separate blocks. Rows inside keep their own side padding; the tile adds a little above and below.
 */
@Composable
internal fun SettingsTile(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
internal fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** A setting with a few values, shown as chips under its title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceItem(
    title: String,
    summary: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in options) {
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
internal fun SettingsItem(title: String, summary: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * A setting with a range of values, on a slider: the value at the top right, what the values mean under the
 * title, then the slider and under it the two ends of the range. [default] is marked on the track, named
 * between the ends and restored by a tap on its name; [band] shades a recommended stretch of the track,
 * named by [bandLabel] in the same place. The value is saved when the thumb is let go. [note] goes under
 * the value, and [above] between the summary and the slider (a choice that switches the slider off, say).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RangeItem(
    title: String,
    summary: String,
    value: Int,
    range: IntRange,
    format: @Composable (Int) -> String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    default: Int? = null,
    band: IntRange? = null,
    bandLabel: String? = null,
    enabled: Boolean = true,
    note: String? = null,
    above: @Composable () -> Unit = {},
) {
    var shown by remember(value) { mutableIntStateOf(value) }
    val text = format(shown)
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        above()
        Slider(
            value = shown.toFloat(),
            onValueChange = { shown = it.roundToInt() },
            onValueChangeFinished = { onChange(shown) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = enabled,
            modifier = Modifier.semantics { stateDescription = text },
            track = { state -> RangeTrack(state, enabled, range, band, default) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(format(range.first), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    bandLabel != null -> Text(
                        bandLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    default != null -> TextButton(
                        onClick = {
                            shown = default
                            onChange(default)
                        },
                        enabled = enabled && shown != default,
                    ) { Text(stringResource(R.string.settings_default, format(default))) }
                }
            }
            Text(format(range.last), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The slider's track without a tick at every step (thirty dots would hide the two that matter): a dot
 * marks the default, and a recommended band is shaded behind the track in the slider's colour, as ranges
 * are behind the lines of the charts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeTrack(state: SliderState, enabled: Boolean, range: IntRange, band: IntRange?, default: Int?) {
    val colors = SliderDefaults.colors()
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = BAND_ALPHA)
    fun fraction(v: Int) = (v - range.first).toFloat() / (range.last - range.first)
    Box(Modifier.fillMaxWidth().height(BAND_HEIGHT), contentAlignment = Alignment.Center) {
        if (band != null) {
            Canvas(Modifier.matchParentSize()) {
                val from = fraction(band.first) * size.width
                val to = fraction(band.last) * size.width
                drawRoundRect(bandColor, Offset(from, 0f), Size(to - from, size.height), CornerRadius(size.height / 2))
            }
        }
        SliderDefaults.Track(sliderState = state, enabled = enabled, drawStopIndicator = null, drawTick = { _, _ -> })
        if (default != null && enabled && default != state.value.roundToInt()) {
            Canvas(Modifier.matchParentSize()) {
                val color = if (default < state.value) colors.activeTickColor else colors.inactiveTickColor
                drawCircle(color, DEFAULT_DOT.toPx(), Offset(fraction(default) * size.width, size.height / 2))
            }
        }
    }
}

private val BAND_HEIGHT = 28.dp
private val DEFAULT_DOT = 3.dp
private const val BAND_ALPHA = 0.22f
