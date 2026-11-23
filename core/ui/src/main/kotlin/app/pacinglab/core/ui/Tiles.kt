package app.pacinglab.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.component.SharedBounds
import app.pacinglab.core.designsystem.component.SharedKeys
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** Two tiles side by side, equally tall; a lone tile keeps its half and leaves the other empty. */
@Composable
fun TileRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The empty half of a row with a single tile. */
@Composable
fun RowScope.EmptyTile(modifier: Modifier = Modifier) {
    Spacer(modifier.weight(1f))
}

/**
 * A small card: label, headline value, one line of context, a mini chart and, at the bottom, a status
 * pill. The pill sits at the foot of the tile so that pills line up across a row.
 */
@Composable
fun Tile(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    chart: @Composable () -> Unit = {},
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers(), maxLines = 1)
            Text(
                text = detail ?: "",
                style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(top = 8.dp)) { chart() }
            if (status != null) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(top = 10.dp)) { status() }
            }
        }
    }
    if (onClick == null) {
        Card(modifier = modifier.fillMaxHeight(), colors = colors) { content() }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxHeight(), colors = colors) { content() }
    }
}

/**
 * What a nightly tile shows: last night's value of [metric] against its range, and the week before it.
 *
 * @property week the measure on each of the last [DAYS] nights, oldest first, as shown (rounded); null without one.
 * @property ranges each of those nights' usual range; the last is last night's.
 * @property rangeNights how many nights last night's range draws on, towards [NightlyRange.MIN_NIGHTS].
 * @property partial the watch shared only part of last night's HRV.
 */
data class NightlyTileData(
    val metric: NightlyMetric,
    val value: Double?,
    val week: List<Double?>,
    val ranges: List<NightlyRange?>,
    val rangeNights: Int,
    val partial: Boolean = false,
) {
    val range: NightlyRange? get() = ranges.lastOrNull()

    companion object {
        /** Days a nightly tile's mini chart shows. */
        const val DAYS = 7

        /**
         * The tile of [metric] on [today] from [nights] (any order), with each day's range already worked out
         * ([ranges], oldest first, [DAYS] of them).
         */
        fun of(
            nights: List<Night>,
            metric: NightlyMetric,
            today: LocalDate,
            ranges: List<NightlyRange?>,
            rangeNights: Int,
        ): NightlyTileData {
            val byDate = nights.associateBy { it.date }
            val last = byDate[today]
            return NightlyTileData(
                metric = metric,
                value = last?.let(metric.value),
                week = (DAYS - 1 downTo 0).map { back -> byDate[today.minusDays(back.toLong())]?.let(metric.value)?.let(metric::rounded) },
                ranges = ranges,
                rangeNights = rangeNights,
                partial = metric == NightlyMetric.HRV && last?.hrvPartial == true,
            )
        }
    }
}

/**
 * A nightly measure against the athlete's own range: last night's value, the range, the week as a
 * sparkline over each day's range, and whether last night fell inside it. [labels] name the week's days.
 */
@Composable
fun NightlyTile(
    data: NightlyTileData,
    labels: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.ROOT,
) {
    val metric = data.metric
    val value = data.value
    val range = data.range
    val colors = chartColors()
    // The tile grows into the measure's page when it opens.
    SharedBounds(SharedKeys.metric(metric.name), modifier) {
        Tile(
            label = stringResource(metric.label()),
            value = value?.let { metric.format(it, locale) } ?: DASH,
            detail = when {
                range != null -> stringResource(
                    R.string.tile_range,
                    metric.format(range.low, locale, unit = false),
                    metric.format(range.high, locale),
                )

                else -> pluralStringResource(
                    R.plurals.tile_range_learning,
                    NightlyRange.MIN_NIGHTS,
                    data.rangeNights,
                    NightlyRange.MIN_NIGHTS,
                )
            },
            onClick = onClick,
            status = when {
                value != null -> ({ RangePill(range?.standing(value)) })

                data.partial -> (
                    {
                        StatusPill(stringResource(R.string.tile_hrv_partial), MaterialTheme.colorScheme.outline, PacingLabIcons.Info)
                    }
                    )

                else -> null
            },
        ) {
            Sparkline(
                values = data.week,
                color = when (metric) {
                    NightlyMetric.HRV -> colors.series[0]
                    NightlyMetric.RESTING_HR -> colors.restingHr
                    NightlyMetric.BREATHING_RATE -> colors.series[2]
                    NightlyMetric.SLEEPING_HR -> colors.restingHr
                    NightlyMetric.OXYGEN_SATURATION -> colors.oxygenSaturation
                    NightlyMetric.SKIN_TEMPERATURE -> colors.skinTemperature
                },
                bands = data.ranges.map { it?.let { r -> r.low..r.high } },
                labels = labels,
            )
        }
    }
}

/** The short name of a nightly measure, as tiles and the vitals card show it. */
fun NightlyMetric.label(): Int = when (this) {
    NightlyMetric.HRV -> R.string.tile_hrv
    NightlyMetric.RESTING_HR -> R.string.tile_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.tile_breathing
    NightlyMetric.SLEEPING_HR -> R.string.tile_sleeping_hr
    NightlyMetric.OXYGEN_SATURATION -> R.string.tile_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.tile_skin_temperature
}

/** "62 ms", "57 bpm", "13.4 /min"; without the unit for the lower end of a range. */
@Composable
fun NightlyMetric.format(value: Double, locale: Locale, unit: Boolean = true): String {
    val number = when (this) {
        NightlyMetric.BREATHING_RATE, NightlyMetric.OXYGEN_SATURATION -> "%.1f".format(locale, value)
        NightlyMetric.SKIN_TEMPERATURE -> "%+.1f".format(locale, value)
        else -> value.roundToInt().toString()
    }
    if (!unit) return number
    return when (this) {
        NightlyMetric.HRV -> stringResource(R.string.unit_ms_text, number)
        NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> stringResource(R.string.unit_bpm_text, number)
        NightlyMetric.BREATHING_RATE -> stringResource(R.string.unit_breaths, number)
        NightlyMetric.OXYGEN_SATURATION -> stringResource(R.string.unit_percent, number)
        NightlyMetric.SKIN_TEMPERATURE -> stringResource(R.string.unit_celsius, number)
    }
}

private const val DASH = "–"
