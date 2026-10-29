package app.pacinglab.feature.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepHealth
import app.pacinglab.core.analytics.recovery.SleepHealthMeasureKind
import app.pacinglab.core.analytics.recovery.SleepMeasureKind
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** What kind of night it was, what held it back, and the stages over the night. */
@Composable
internal fun NightCard(night: Night, score: SleepScore, locale: Locale, modifier: Modifier = Modifier, rested: Int? = null) {
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val labels = STAGE_LANES.map { type ->
        stringResource(R.string.sleep_lane, stringResource(type.label()), Formats.hoursMinutes(night.stageMillis[type] ?: 0))
    }
    SleepCard(stringResource(score.rating.headline()), stringResource(R.string.sleep_stages_info), modifier) {
        Text(summary(score), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (night.stages.isNotEmpty()) StageLanes(night, labels, stringResource(R.string.sleep_stages_description))
        Row(Modifier.fillMaxWidth()) {
            TimeLabel(
                time.format(night.start.atZone(night.zone)),
                stringResource(R.string.sleep_went_to_bed),
                TextAlign.Start,
                Modifier.weight(1f),
            )
            TimeLabel(clock(night.midpointMinuteOfDay), stringResource(R.string.sleep_middle), TextAlign.Center, Modifier.weight(1f))
            TimeLabel(time.format(night.end.atZone(night.zone)), stringResource(R.string.sleep_got_up), TextAlign.End, Modifier.weight(1f))
        }
        if (rested != null) {
            Text(
                text = stringResource(R.string.sleep_felt, rested, stringArrayResource(R.array.sleep_rested_words)[rested - 1]),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeLabel(time: String, caption: String, align: TextAlign, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(time, style = MaterialTheme.typography.titleMedium.tabularNumbers(), textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(
            caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One sentence on what held the night back, from the measures below their threshold. */
@Composable
private fun summary(score: SleepScore): String {
    val weak = score.measures.filter { !it.withinThreshold }.sortedBy { it.fraction }.map { stringResource(it.kind.shortfall()) }
    val fair = score.measures.filter {
        it.withinThreshold && it.fraction < NEARLY_FULL
    }.sortedBy { it.fraction }.map { stringResource(it.kind.shortfall()) }
    val and = stringResource(R.string.sleep_summary_and)
    return when {
        weak.isNotEmpty() -> stringResource(R.string.sleep_summary_held_back, weak.take(2).joinToString(" $and "))
        fair.isNotEmpty() -> stringResource(R.string.sleep_summary_could_be_better, fair.take(2).joinToString(" $and "))
        else -> stringResource(R.string.sleep_summary_excellent)
    }
}

/** The night's score in its two parts. */
@Composable
internal fun ScoreCard(night: Night, score: SleepScore, needMillis: Long, modifier: Modifier = Modifier) {
    val parts = if (score.continuity == null) 1 else 2
    SleepCard(stringResource(R.string.sleep_score_parts), stringResource(R.string.sleep_score_info), modifier) {
        Part(
            name = stringResource(R.string.sleep_part_duration),
            fraction = score.duration,
            points = if (parts == 1) 100.0 else SleepScore.DURATION_POINTS,
            detail = stringResource(
                R.string.sleep_detail_duration,
                Formats.hoursMinutes(night.asleepMillis),
                Formats.hoursMinutes(needMillis),
            ),
        )
        score.continuity?.let { continuity ->
            Part(
                name = stringResource(R.string.sleep_part_continuity),
                fraction = continuity,
                points = SleepScore.CONTINUITY_POINTS,
                detail = stringResource(R.string.sleep_detail_continuity),
            )
        }
    }
}

/** The two weeks' sleep health: its score and its four parts. */
@Composable
internal fun HealthCard(health: SleepHealth, needMillis: Long, modifier: Modifier = Modifier) {
    val points = 100.0 / health.measures.size
    SleepCard(
        stringResource(R.string.sleep_health_card_title, health.score, stringResource(health.rating.label())),
        stringResource(R.string.sleep_health_info),
        modifier,
    ) {
        Text(
            text = pluralStringResource(R.plurals.sleep_health_over, health.nights, health.nights),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (measure in health.measures) {
            val (name, detail) = when (measure.kind) {
                SleepHealthMeasureKind.REGULARITY ->
                    R.string.sleep_part_regularity to stringResource(R.string.sleep_detail_regularity_index, measure.value.roundToInt())

                SleepHealthMeasureKind.DURATION -> R.string.sleep_part_duration to stringResource(
                    R.string.sleep_detail_usual_duration,
                    Formats.hoursMinutes((measure.value * needMillis).toLong()),
                    (measure.value * 100).roundToInt(),
                )

                SleepHealthMeasureKind.EFFICIENCY ->
                    R.string.sleep_part_efficiency to
                        stringResource(R.string.sleep_detail_usual_efficiency, (measure.value * 100).roundToInt())

                SleepHealthMeasureKind.TIMING ->
                    R.string.sleep_part_timing to stringResource(R.string.sleep_detail_usual_timing, clock(health.usualMidpointMinute))
            }
            Part(name = stringResource(name), fraction = measure.fraction, points = points, detail = detail)
        }
    }
}

/** One part of a score: its share of points as a bar, the points and what was measured. */
@Composable
private fun Part(name: String, fraction: Double, points: Double, detail: String, modifier: Modifier = Modifier) {
    val earned = (fraction * points).roundToInt()
    val total = points.roundToInt()
    val bar = chartColors().series[0]
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name: $earned of $total. $detail" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(fraction.toFloat()).height(8.dp).background(bar, RoundedCornerShape(4.dp)))
            }
            Text(
                text = stringResource(R.string.sleep_points, earned, total),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
        Text(detail, style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun SleepRating.headline(): Int = when (this) {
    SleepRating.GOOD -> R.string.sleep_headline_good
    SleepRating.FAIR -> R.string.sleep_headline_fair
    SleepRating.POOR -> R.string.sleep_headline_poor
}

private fun SleepMeasureKind.shortfall(): Int = when (this) {
    SleepMeasureKind.DURATION -> R.string.sleep_shortfall_duration
    SleepMeasureKind.EFFICIENCY -> R.string.sleep_shortfall_efficiency
    SleepMeasureKind.AWAKE_AFTER_ONSET -> R.string.sleep_shortfall_awake
    SleepMeasureKind.TIME_TO_SLEEP -> R.string.sleep_shortfall_time_to_sleep
    SleepMeasureKind.LONG_AWAKENINGS -> R.string.sleep_shortfall_awakenings
}

/** A measure this close to full points counts as excellent in the summary. */
private const val NEARLY_FULL = 0.85
