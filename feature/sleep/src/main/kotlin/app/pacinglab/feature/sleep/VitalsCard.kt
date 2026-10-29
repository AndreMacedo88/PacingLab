package app.pacinglab.feature.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.designsystem.chart.Sparkline
import app.pacinglab.core.designsystem.chart.weekdayInitials
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The night's vitals, as Apple's Vitals app shows them: HRV, resting heart rate, breathing rate, SpO2 and
 * skin temperature against the athlete's own range, with the week before as sparklines over each night's
 * range, and a line on top that warns when two or more are outside at once. Each row opens the
 * measure's detail page.
 */
@Composable
internal fun VitalsCard(nights: List<Night>, night: Night, onOpenMetric: (NightlyMetric) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = chartColors()
    val days = (WEEK - 1 downTo 0).map { night.date.minusDays(it) }
    val labels = weekdayInitials(days, locale)
    val byDate = nights.associateBy { it.date }
    val vitals = remember(nights, night) { Vitals.of(nights, night.date) }
    SleepCard(stringResource(R.string.sleep_body_title), stringResource(R.string.sleep_body_info), modifier) {
        vitals?.takeIf { it.judged > 0 }?.let { VitalsVerdict(it) }
        val rows = listOf(
            NightlyMetric.HRV to colors.series[0],
            NightlyMetric.RESTING_HR to colors.restingHr,
            NightlyMetric.BREATHING_RATE to colors.series[2],
            NightlyMetric.OXYGEN_SATURATION to colors.oxygenSaturation,
            NightlyMetric.SKIN_TEMPERATURE to colors.skinTemperature,
        ).filter { (metric, _) -> metric !in SECONDARY_ONLY || days.any { day -> byDate[day]?.let(metric.value) != null } }
        for ((metric, color) in rows) {
            val ranges = remember(nights, night, metric) { days.map { NightlyRange.of(nights, metric, it) } }
            val value = metric.value(night)
            val range = ranges.last()
            val partial = metric == NightlyMetric.HRV && night.hrvPartial
            BodyRow(
                name = stringResource(metric.label()),
                value = value?.let { metric.formatted(it, locale) },
                usual = when {
                    partial -> stringResource(R.string.sleep_hrv_partial)

                    range != null -> stringResource(
                        R.string.sleep_range,
                        metric.number(range.low, locale),
                        metric.formatted(range.high, locale),
                    )

                    else -> null
                },
                values = days.map { day -> byDate[day]?.let(metric.value)?.let(metric::rounded) },
                bands = ranges.map { it?.let { r -> r.low..r.high } },
                color = color,
                labels = labels,
                status = value?.let { { RangePill(range?.standing(it)) } },
                onClick = { onOpenMetric(metric) },
            )
        }
    }
}

/** How many measures left their range, naming them; two or more is a warning. */
@Composable
private fun VitalsVerdict(vitals: Vitals, modifier: Modifier = Modifier) {
    val outside = vitals.outside
    val names = outside.map { stringResource(it.metric.label()) }.joinToString(", ")
    val text = when {
        outside.isEmpty() -> stringResource(R.string.vitals_all_typical)
        vitals.alert -> pluralStringResource(R.plurals.vitals_alert, outside.size, outside.size, names)
        else -> stringResource(R.string.vitals_one_outside, names)
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(
                when {
                    outside.isEmpty() -> PacingLabIcons.CheckCircle
                    vitals.alert -> PacingLabIcons.Warning
                    else -> PacingLabIcons.Info
                },
            ),
            contentDescription = null,
            tint = when {
                outside.isEmpty() -> StatusColors.Good
                vitals.alert -> StatusColors.Warning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BodyRow(
    name: String,
    value: String?,
    usual: String?,
    values: List<Double?>,
    bands: List<ClosedFloatingPointRange<Double>?>,
    color: Color,
    labels: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: DASH, style = MaterialTheme.typography.titleLarge.tabularNumbers())
            usual?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status?.let { Box(Modifier.padding(top = 4.dp)) { it() } }
        }
        Box(Modifier.weight(1f)) { Sparkline(values = values, color = color, bands = bands, labels = labels) }
    }
}

/** Measures only the Google Health API supplies; their rows show once a recent night has a value. */
private val SECONDARY_ONLY = setOf(NightlyMetric.OXYGEN_SATURATION, NightlyMetric.SKIN_TEMPERATURE)

private fun NightlyMetric.label(): Int = when (this) {
    NightlyMetric.HRV -> R.string.sleep_hrv
    NightlyMetric.RESTING_HR -> R.string.sleep_resting_hr
    NightlyMetric.BREATHING_RATE -> R.string.sleep_breathing
    NightlyMetric.SLEEPING_HR -> R.string.sleep_heart_rate_lowest
    NightlyMetric.OXYGEN_SATURATION -> R.string.sleep_oxygen
    NightlyMetric.SKIN_TEMPERATURE -> R.string.sleep_skin_temperature
}

private fun NightlyMetric.number(value: Double, locale: Locale): String = when (this) {
    NightlyMetric.BREATHING_RATE, NightlyMetric.OXYGEN_SATURATION -> "%.1f".format(locale, value)
    NightlyMetric.SKIN_TEMPERATURE -> "%+.1f".format(locale, value)
    else -> value.roundToInt().toString()
}

@Composable
private fun NightlyMetric.formatted(value: Double, locale: Locale): String = when (this) {
    NightlyMetric.HRV -> stringResource(R.string.unit_ms, value.roundToInt())
    NightlyMetric.RESTING_HR, NightlyMetric.SLEEPING_HR -> stringResource(R.string.unit_bpm, value.roundToInt())
    NightlyMetric.BREATHING_RATE -> stringResource(R.string.unit_breaths, number(value, locale))
    NightlyMetric.OXYGEN_SATURATION -> stringResource(R.string.unit_percent, number(value, locale))
    NightlyMetric.SKIN_TEMPERATURE -> stringResource(R.string.unit_celsius, number(value, locale))
}
