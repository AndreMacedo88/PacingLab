package app.pacinglab.feature.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepMeasureKind
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.SleepStageType
import java.util.Locale
import kotlin.math.roundToInt

/** Continuity and stage measures against their published ranges. */
@Composable
internal fun QualityCard(night: Night, score: SleepScore, modifier: Modifier = Modifier) {
    val c = night.continuity ?: return
    SleepCard(stringResource(R.string.sleep_quality_title), stringResource(R.string.sleep_quality_info), modifier) {
        val latency = c.latencyMillis / MINUTE
        QualityRow(
            stringResource(R.string.sleep_quality_time_to_sleep),
            stringResource(R.string.sleep_minutes, latency.roundToInt()),
            latency,
            0.0..30.0,
            0.0..90.0,
        )
        val awake = c.wakeAfterOnsetMillis / MINUTE
        QualityRow(
            stringResource(R.string.sleep_quality_awake),
            stringResource(R.string.sleep_minutes, awake.roundToInt()),
            awake,
            0.0..20.0,
            0.0..90.0,
        )
        QualityRow(
            stringResource(R.string.sleep_quality_awakenings),
            pluralStringResource(R.plurals.sleep_awakenings, c.longAwakenings, c.longAwakenings),
            c.longAwakenings.toDouble(),
            0.0..1.0,
            0.0..6.0,
        )
        score.measure(SleepMeasureKind.EFFICIENCY)?.let {
            val percent = it.value * 100
            QualityRow(
                stringResource(R.string.sleep_quality_efficiency),
                stringResource(R.string.sleep_percent, percent.roundToInt()),
                percent,
                85.0..100.0,
                60.0..100.0,
            )
        }
        for (type in listOf(SleepStageType.DEEP, SleepStageType.REM)) {
            val typical = type.typical() ?: continue
            val millis = night.stageMillis[type] ?: 0L
            val share = 100.0 * millis / night.asleepMillis
            QualityRow(
                stringResource(R.string.sleep_quality_stage, stringResource(type.label())),
                stringResource(R.string.sleep_stage_value, Formats.hoursMinutes(millis), share.roundToInt()),
                share,
                typical.first.toDouble()..typical.last.toDouble(),
                0.0..40.0,
            )
        }
    }
}

@Composable
private fun QualityRow(
    name: String,
    value: String,
    measured: Double,
    range: ClosedFloatingPointRange<Double>,
    track: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
) {
    val inRange = measured in range
    val status = stringResource(
        when {
            inRange -> R.string.sleep_in_range
            measured > range.endInclusive -> R.string.sleep_above_range
            else -> R.string.sleep_below_range
        },
    )
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$name $value, $status"
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$name · $value", style = MaterialTheme.typography.bodyMedium.tabularNumbers(), modifier = Modifier.weight(1f))
            StatusChip(status, inRange)
        }
        RangeBar(value = measured, range = range, track = track)
    }
}

/** Heart rate through the night with its average, its lowest stretch and the night's resting value. */
@Composable
internal fun HeartRateCard(night: Night, locale: Locale, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val sleeping = night.sleepingHeartRate ?: return
    SleepCard(stringResource(R.string.sleep_heart_rate_title), stringResource(R.string.sleep_heart_rate_info), modifier, onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Value(stringResource(R.string.sleep_heart_rate_average), stringResource(R.string.unit_bpm, sleeping.average.roundToInt()))
            Value(stringResource(R.string.sleep_heart_rate_lowest), stringResource(R.string.unit_bpm, sleeping.lowest.roundToInt()))
            night.restingHr?.let {
                Value(stringResource(R.string.sleep_resting_hr_short), stringResource(R.string.unit_bpm, it.roundToInt()))
            }
        }
        SleepingHeartRateChart(
            night = night,
            sleeping = sleeping,
            restingLabel = night.restingHr?.let { stringResource(R.string.sleep_heart_rate_resting_line, it.roundToInt()) },
            description = stringResource(
                R.string.sleep_heart_rate_description,
                sleeping.average.roundToInt(),
                sleeping.lowest.roundToInt(),
            ),
            locale = locale,
        )
    }
}

@Composable
private fun Value(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.tabularNumbers())
    }
}

internal fun SleepStageType.label(): Int = when (this) {
    SleepStageType.DEEP -> R.string.stage_deep
    SleepStageType.REM -> R.string.stage_rem
    SleepStageType.AWAKE, SleepStageType.OUT_OF_BED -> R.string.stage_awake
    else -> R.string.stage_light
}

/** Typical share of sleep for healthy adults, in percent; awake has none. */
internal fun SleepStageType.typical(): IntRange? = when (this) {
    SleepStageType.DEEP -> 13..23
    SleepStageType.REM -> 20..25
    SleepStageType.LIGHT -> 45..55
    else -> null
}
