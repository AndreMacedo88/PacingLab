package app.pacinglab.feature.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.FactorEffect
import app.pacinglab.core.analytics.recovery.FactorGroup
import app.pacinglab.core.analytics.recovery.FactorUnit
import app.pacinglab.core.analytics.recovery.OutcomeFactors
import app.pacinglab.core.analytics.recovery.SleepFactor
import app.pacinglab.core.analytics.recovery.SleepFactors
import app.pacinglab.core.analytics.recovery.SleepOutcome
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What goes with better and worse nights ([SleepFactors]): a switch between the sleep score and how rested
 * the athlete felt, then one row per factor with its likely effect as a dot and the middle 80 % as a bar,
 * on a shared scale from worse to better around a grey band of effects too small to matter. Clear factors
 * are coloured by direction; the rest stay grey. Tapping a row opens its nights in a sheet. Until there
 * are [SleepFactors.MIN_NIGHTS] nights the card counts towards them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepFactorsCard(factors: SleepFactors?, modifier: Modifier = Modifier, initialOutcome: SleepOutcome = SleepOutcome.SCORE) {
    var outcome by rememberSaveable { mutableStateOf(initialOutcome) }
    var opened by rememberSaveable { mutableStateOf<SleepFactor?>(null) }
    SleepCard(
        title = stringResource(R.string.factors_title),
        info = stringResource(R.string.factors_info),
        advanced = stringResource(R.string.factors_advanced),
        modifier = modifier,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SleepOutcome.entries.forEachIndexed { i, entry ->
                SegmentedButton(
                    selected = outcome == entry,
                    onClick = { outcome = entry },
                    shape = SegmentedButtonDefaults.itemShape(i, SleepOutcome.entries.size),
                    icon = {},
                ) { Text(stringResource(entry.label()), maxLines = 1) }
            }
        }
        if (factors == null) {
            Text(
                text = stringResource(R.string.factors_computing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutcomeContent(factors.of(outcome), onOpen = { opened = it.factor })
        }
    }
    val effect = opened?.let { factor -> factors?.of(outcome)?.effects?.firstOrNull { it.factor == factor } }
    if (effect != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { opened = null }, sheetState = sheet) {
            FactorDetail(
                effect,
                outcome,
                factors?.of(outcome)?.predictive == true,
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun OutcomeContent(result: OutcomeFactors, onOpen: (FactorEffect) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (result.learning) LearningContent(result) else Estimates(result, onOpen)
    }
}

@Composable
private fun LearningContent(result: OutcomeFactors) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val learning = if (result.outcome == SleepOutcome.SCORE) R.plurals.factors_learning_score else R.plurals.factors_learning_rested
        Text(
            text = pluralStringResource(learning, result.nights, result.nights, SleepFactors.MIN_NIGHTS),
            style = MaterialTheme.typography.titleSmall,
        )
        LinearProgressIndicator(
            progress = { result.nights.toFloat() / SleepFactors.MIN_NIGHTS },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Text(
            text = stringResource(R.string.factors_learning_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Estimates(result: OutcomeFactors, onOpen: (FactorEffect) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val clearest = result.effects.filter { it.clear }.maxByOrNull { it.chance }
        Text(
            text = when {
                !result.predictive && clearest == null -> stringResource(R.string.factors_not_predictive)

                clearest != null -> stringResource(
                    R.string.factors_clearest,
                    factorRow(clearest, locale),
                    effectValue(clearest.mean, result.outcome, locale, words = true),
                )

                else -> stringResource(R.string.factors_none_clear)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (result.effects.isNotEmpty()) {
            val scale = scaleOf(result)
            Column {
                ScaleLabels(stringResource(R.string.factors_worse), stringResource(R.string.factors_better))
                for (group in FactorGroup.entries) {
                    val rows = result.effects.filter { it.factor.group == group }
                    if (rows.isEmpty()) continue
                    Text(
                        text = stringResource(group.label()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    for (effect in rows) FactorRow(effect, result.outcome, scale, locale, onClick = { onOpen(effect) })
                }
                ScaleLabels(effectValue(-scale, result.outcome, locale), effectValue(scale, result.outcome, locale), middle = "0")
            }
        }
        val notes = buildList {
            if (result.waiting.isNotEmpty()) {
                add(stringResource(R.string.factors_waiting, result.waiting.map { stringResource(it.name()) }.joinToString(", ")))
            }
            if (result.excluded > 0) add(pluralStringResource(R.plurals.factors_excluded, result.excluded, result.excluded))
        }
        for (note in notes) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Labels over the plot column: the ends of the scale, and its middle when given. */
@Composable
private fun ScaleLabels(left: String, right: String, middle: String? = null) {
    val style = MaterialTheme.typography.labelSmall.tabularNumbers()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f))
        Box(Modifier.width(PLOT_WIDTH)) {
            Text(left, style = style, color = color, modifier = Modifier.align(Alignment.CenterStart))
            if (middle != null) Text(middle, style = style, color = color, modifier = Modifier.align(Alignment.Center))
            Text(right, style = style, color = color, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Box(Modifier.width(VALUE_WIDTH))
    }
}

@Composable
private fun FactorRow(effect: FactorEffect, outcome: SleepOutcome, scale: Double, locale: Locale, onClick: () -> Unit) {
    val label = factorRow(effect, locale)
    val value = effectValue(effect.mean, outcome, locale)
    val state = stringResource(if (effect.clear) R.string.factors_detail_clear else R.string.factors_detail_unclear)
    val description =
        stringResource(R.string.factors_detail_description, label, effectValue(effect.mean, outcome, locale, words = true), state)
    val text = if (effect.clear) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = text, modifier = Modifier.weight(1f).padding(end = 8.dp))
        EffectBar(effect, outcome, scale, Modifier.width(PLOT_WIDTH).height(28.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.tabularNumbers(),
            color = text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(VALUE_WIDTH),
        )
    }
}

/** The likely effect as a dot with its 80 % range as a bar, over the band of effects too small to matter. */
@Composable
private fun EffectBar(effect: FactorEffect, outcome: SleepOutcome, scale: Double, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = when {
        !effect.clear -> colors.muted
        effect.mean > 0 -> colors.positive
        else -> colors.negative
    }
    val band = MaterialTheme.colorScheme.onSurface.copy(alpha = BAND_ALPHA)
    val zero = colors.baseline
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier) {
        val pad = DOT_RADIUS.toPx() + 1.dp.toPx()
        fun x(v: Double) = (size.width / 2 + (v / scale).coerceIn(-1.0, 1.0) * (size.width / 2 - pad)).toFloat()
        val minimum = outcome.minimum
        drawRoundRect(band, Offset(x(-minimum), 0f), Size(x(minimum) - x(-minimum), size.height), CornerRadius(2.dp.toPx()))
        drawLine(zero, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx())
        val y = size.height / 2
        drawLine(
            ink.copy(alpha = if (effect.clear) 1f else BAR_ALPHA),
            Offset(x(effect.low), y),
            Offset(x(effect.high), y),
            3.dp.toPx(),
            StrokeCap.Round,
        )
        drawCircle(ring, DOT_RADIUS.toPx() + 1.5.dp.toPx(), Offset(x(effect.mean), y))
        drawCircle(ink, DOT_RADIUS.toPx(), Offset(x(effect.mean), y))
    }
}

/** The effect, its range and certainty, and the nights behind it, with every other factor held equal. */
@Composable
internal fun FactorDetail(effect: FactorEffect, outcome: SleepOutcome, predictive: Boolean, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(effect.factor.name()), style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(effectValue(effect.mean, outcome, locale), style = MaterialTheme.typography.headlineMedium.tabularNumbers())
            val condition = factorRow(effect, locale).replaceFirstChar { it.lowercase(locale) }
            val rounded = abs(effect.mean).roundToInt()
            Text(
                text = if (outcome == SleepOutcome.SCORE) {
                    pluralStringResource(R.plurals.factors_detail_points_for, if (rounded == 1) 1 else 2, condition)
                } else {
                    stringResource(R.string.factors_detail_rating_for, condition)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
            )
        }
        Text(
            text = stringResource(
                R.string.factors_detail_range,
                effectValue(effect.low, outcome, locale),
                effectValue(effect.high, outcome, locale),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        val chance = if (outcome == SleepOutcome.SCORE) R.string.factors_detail_chance_score else R.string.factors_detail_chance_rested
        val percent = (effect.chance * 100).toInt().coerceAtMost(99)
        Text(
            text = when {
                effect.clear -> stringResource(R.string.factors_detail_clear) + ". " + stringResource(chance, percent)
                !predictive -> stringResource(R.string.factors_detail_unreliable)
                else -> stringResource(R.string.factors_detail_unclear) + ". " + stringResource(chance, percent)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = effect.yes?.let { pluralStringResource(R.plurals.factors_detail_nights_yes, effect.nights, effect.nights, it) }
                ?: pluralStringResource(R.plurals.factors_detail_nights, effect.nights, effect.nights),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FactorScatter(effect, outcome, locale, Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp))
        Text(
            text = stringResource(R.string.factors_detail_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The nights by the factor's value, the outcome adjusted for the other factors, and the estimated line. */
@Composable
private fun FactorScatter(effect: FactorEffect, outcome: SleepOutcome, locale: Locale, modifier: Modifier = Modifier) {
    val colors = chartColors()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val line = if (!effect.clear) {
        colors.muted
    } else if (effect.mean > 0) {
        colors.positive
    } else {
        colors.negative
    }
    val dot = colors.series[0]
    val style = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val no = stringResource(R.string.factors_detail_no)
    val yes = stringResource(R.string.factors_detail_yes)
    val points = effect.points
    if (points.isEmpty()) return
    val low = points.minOf { it.adjusted }
    val high = points.maxOf { it.adjusted }
    val step = if (outcome == SleepOutcome.SCORE) 10.0 else 1.0
    val yLow = floor(low / step) * step
    val yHigh = ceil(high / step) * step + if (ceil(high / step) * step == yLow) step else 0.0
    val xs = points.map { it.value }
    val xLow = xs.min()
    val xHigh = xs.max().let { if (it == xLow) it + 1 else it }
    val ticks = if (effect.factor.binary) emptyList() else listOf(xLow, (xLow + xHigh) / 2, xHigh)
    val tickLabels = ticks.map { factorValue(effect.factor, it, locale) }
    Canvas(modifier) {
        val left = 32.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val right = size.width - 8.dp.toPx()
        fun y(v: Double) = (bottom - (v - yLow) / (yHigh - yLow) * (bottom - 4.dp.toPx())).toFloat()
        fun x(v: Double): Float = if (effect.factor.binary) {
            (left + (right - left) * (0.25 + 0.5 * v)).toFloat()
        } else {
            (left + (v - xLow) / (xHigh - xLow) * (right - left)).toFloat()
        }
        var v = yLow
        while (v <= yHigh + 1e-9) {
            drawLine(colors.grid, Offset(left, y(v)), Offset(right, y(v)), 1.dp.toPx())
            val label = measurer.measure(
                if (step >= 1 &&
                    outcome == SleepOutcome.SCORE
                ) {
                    v.roundToInt().toString()
                } else {
                    String.format(locale, "%.0f", v)
                },
                style,
            )
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(v) - label.size.height / 2f))
            v += step
        }
        for (point in points) {
            // A steady sideways spread for yes-or-no factors, so that equal nights do not hide each other.
            val jitter = if (effect.factor.binary) ((point.date.toEpochDay() * 37 % 21) - 10) / 10.0 * 0.08 else 0.0
            drawCircle(dot.copy(alpha = DOT_ALPHA), 4.dp.toPx(), Offset(x(point.value + jitter), y(point.adjusted)))
        }
        fun fitted(v: Double) = effect.anchorValue + effect.slope * (v - effect.anchor)
        if (effect.factor.binary) {
            for (side in listOf(0.0, 1.0)) {
                val half = (right - left) * 0.12f
                drawLine(
                    line,
                    Offset(x(side) - half, y(fitted(side))),
                    Offset(x(side) + half, y(fitted(side))),
                    3.dp.toPx(),
                    StrokeCap.Round,
                )
                val label = measurer.measure(if (side == 0.0) no else yes, style)
                drawText(label, topLeft = Offset(x(side) - label.size.width / 2f, bottom + 4.dp.toPx()))
            }
        } else {
            drawLine(line, Offset(x(xLow), y(fitted(xLow))), Offset(x(xHigh), y(fitted(xHigh))), 3.dp.toPx(), StrokeCap.Round)
            ticks.forEachIndexed { i, tick ->
                val label = measurer.measure(tickLabels[i], style)
                val at = (x(tick) - label.size.width / 2f).coerceIn(left, right - label.size.width)
                drawText(label, topLeft = Offset(at, bottom + 4.dp.toPx()))
            }
        }
    }
}

/** A symmetric scale holding every bar and at least three times the minimum effect, rounded up to a readable end. */
private fun scaleOf(result: OutcomeFactors): Double {
    val reach = result.effects.maxOf { maxOf(abs(it.low), abs(it.high)) }.coerceAtLeast(3 * result.outcome.minimum)
    val ends = if (result.outcome == SleepOutcome.SCORE) SCORE_ENDS else RATING_ENDS
    return ends.firstOrNull { it >= reach } ?: (ceil(reach / ends.last()) * ends.last())
}

/** A signed effect: whole points of score, tenths of the rating; with [words], the unit spelled out. */
@Composable
private fun effectValue(value: Double, outcome: SleepOutcome, locale: Locale, words: Boolean = false): String {
    val digits = if (outcome == SleepOutcome.SCORE && abs(value) >= 1) 0 else 1
    val number = String.format(locale, "%+.${digits}f", value).replace('-', '−')
    if (!words) return number
    return if (outcome == SleepOutcome.SCORE) {
        pluralStringResource(R.plurals.factors_points, if (abs(value) >= 0.5 && abs(value) < 1.5) 1 else 2, number)
    } else {
        stringResource(R.string.factors_rating, number)
    }
}

/** The row label: the factor with its typical change. */
@Composable
private fun factorRow(effect: FactorEffect, locale: Locale): String {
    val step = factorValue(effect.factor, effect.step, locale, step = true)
    return when (effect.factor) {
        SleepFactor.TRAINING_LOAD -> stringResource(R.string.factors_row_training_load, step)
        SleepFactor.LATE_TRAINING -> stringResource(R.string.factors_row_late_training)
        SleepFactor.FORM -> stringResource(R.string.factors_row_form, step)
        SleepFactor.BEDTIME -> stringResource(R.string.factors_row_bedtime, step)
        SleepFactor.PREVIOUS_NIGHT -> stringResource(R.string.factors_row_previous_night, step)
        SleepFactor.FREE_DAY -> stringResource(R.string.factors_row_free_day)
        SleepFactor.DAYTIME_STRAIN -> stringResource(R.string.factors_row_strain, step)
        SleepFactor.TIME_ASLEEP -> stringResource(R.string.factors_row_time_asleep, step)
        SleepFactor.AWAKE -> stringResource(R.string.factors_row_awake, step)
        SleepFactor.DEEP_BEFORE_WAKING -> stringResource(R.string.factors_row_deep)
        SleepFactor.HRV -> stringResource(R.string.factors_row_hrv, step)
        SleepFactor.SLEEPING_HR -> stringResource(R.string.factors_row_sleeping_hr, step)
        SleepFactor.SKIN_TEMPERATURE -> stringResource(R.string.factors_row_skin, step)
    }
}

/**
 * A value of [factor] in its unit, or with [step] the size of a typical change in it. Values that are
 * differences from the usual (the night before, HRV) carry a sign; bedtimes read as clock times.
 */
@Composable
private fun factorValue(factor: SleepFactor, value: Double, locale: Locale, step: Boolean = false): String {
    val signed = !step && factor !in setOf(SleepFactor.BEDTIME, SleepFactor.AWAKE, SleepFactor.TRAINING_LOAD)
    fun number(digits: Int) = String.format(locale, if (signed) "%+.${digits}f" else "%.${digits}f", value).replace('-', '−')
    return when (factor.unit) {
        FactorUnit.MINUTES -> if (factor == SleepFactor.BEDTIME && !step) {
            clock(((value.roundToInt() + EVENING_MINUTE) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY)
        } else {
            val minutes = value.roundToInt()
            val sign = if (signed && minutes > 0) {
                "+"
            } else if (minutes < 0) {
                "−"
            } else {
                ""
            }
            val whole = abs(minutes)
            sign + if (whole >= 60) {
                stringResource(R.string.factors_hours_minutes, whole / 60, whole % 60)
            } else {
                stringResource(R.string.factors_minutes, whole)
            }
        }

        FactorUnit.STRESS, FactorUnit.FORM -> number(0)

        FactorUnit.PERCENT -> stringResource(R.string.factors_percent, number(0))

        FactorUnit.BPM -> stringResource(R.string.factors_bpm, number(if (abs(value) < 10 && value % 1 != 0.0) 1 else 0))

        FactorUnit.CELSIUS -> stringResource(R.string.factors_celsius, number(1))

        FactorUnit.NONE -> stringResource(if (value >= 0.5) R.string.factors_detail_yes else R.string.factors_detail_no)
    }
}

private fun SleepOutcome.label(): Int = when (this) {
    SleepOutcome.SCORE -> R.string.factors_outcome_score
    SleepOutcome.RESTED -> R.string.factors_outcome_rested
}

private fun FactorGroup.label(): Int = when (this) {
    FactorGroup.BEFORE -> R.string.factors_group_before
    FactorGroup.NIGHT -> R.string.factors_group_night
    FactorGroup.BODY -> R.string.factors_group_body
}

private fun SleepFactor.name(): Int = when (this) {
    SleepFactor.TRAINING_LOAD -> R.string.factors_name_training_load
    SleepFactor.LATE_TRAINING -> R.string.factors_name_late_training
    SleepFactor.FORM -> R.string.factors_name_form
    SleepFactor.BEDTIME -> R.string.factors_name_bedtime
    SleepFactor.PREVIOUS_NIGHT -> R.string.factors_name_previous_night
    SleepFactor.FREE_DAY -> R.string.factors_name_free_day
    SleepFactor.DAYTIME_STRAIN -> R.string.factors_name_strain
    SleepFactor.TIME_ASLEEP -> R.string.factors_name_time_asleep
    SleepFactor.AWAKE -> R.string.factors_name_awake
    SleepFactor.DEEP_BEFORE_WAKING -> R.string.factors_name_deep
    SleepFactor.HRV -> R.string.factors_name_hrv
    SleepFactor.SLEEPING_HR -> R.string.factors_name_sleeping_hr
    SleepFactor.SKIN_TEMPERATURE -> R.string.factors_name_skin
}

private val PLOT_WIDTH: Dp = 112.dp
private val VALUE_WIDTH: Dp = 44.dp
private val DOT_RADIUS: Dp = 5.dp
private const val BAND_ALPHA = 0.07f
private const val BAR_ALPHA = 0.6f
private const val DOT_ALPHA = 0.7f
private const val EVENING_MINUTE = 18 * 60
private const val MINUTES_PER_DAY = 24 * 60
private val SCORE_ENDS = listOf(3.0, 5.0, 10.0, 15.0, 20.0, 30.0)
private val RATING_ENDS = listOf(0.3, 0.5, 1.0, 1.5, 2.0)
