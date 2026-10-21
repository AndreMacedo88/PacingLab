package app.pacinglab.feature.workouts.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.session.PhaseSummary
import app.pacinglab.core.analytics.session.PhaseTotals
import app.pacinglab.core.analytics.session.SessionAnalysis
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.feature.workouts.R
import java.util.Locale

/**
 * Reps and recoveries of an interval session: what the running and the recovering added up to, then
 * every rep with the recovery after it. Tapping a pace switches all of them between pace and speed.
 */
@Composable
internal fun IntervalsCard(analysis: SessionAnalysis, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    val work = analysis.totals(PhaseKind.WORK) ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardHeader(
                title =
                stringResource(R.string.intervals_title) + " · " +
                    pluralStringResource(R.plurals.intervals_reps, work.count, work.count),
                info = stringResource(R.string.intervals_info),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Totals(stringResource(R.string.intervals_running), work, toggle, locale, Modifier.weight(1f))
                analysis.totals(PhaseKind.RECOVERY)?.let {
                    Totals(stringResource(R.string.intervals_recovery), it, toggle, locale, Modifier.weight(1f))
                }
            }
            analysis.totals(PhaseKind.WARM_UP, PhaseKind.COOL_DOWN)?.let { easy -> EasyLine(easy, toggle.showSpeed, locale) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            RepTable(analysis.phases, toggle, locale)
        }
    }
}

@Composable
private fun Totals(label: String, totals: PhaseTotals, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        totals.distanceMeters?.let {
            Text(
                stringResource(R.string.unit_km, Formats.kilometres(it, locale)),
                style = MaterialTheme.typography.headlineSmall.tabularNumbers(),
            )
        }
        Text(Formats.duration(totals.durationMillis), style = MaterialTheme.typography.bodyLarge.tabularNumbers())
        paceText(totals.durationMillis, totals.distanceMeters, toggle.showSpeed, locale)?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge.tabularNumbers(), modifier = Modifier.speedToggle(toggle))
        }
        totals.averageHr?.let {
            Text(
                text = stringResource(R.string.intervals_hr, it),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EasyLine(totals: PhaseTotals, showSpeed: Boolean, locale: Locale, modifier: Modifier = Modifier) {
    val parts = listOfNotNull(
        totals.distanceMeters?.let { stringResource(R.string.unit_km, Formats.kilometres(it, locale)) },
        Formats.duration(totals.durationMillis),
        paceText(totals.durationMillis, totals.distanceMeters, showSpeed, locale),
    )
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.intervals_easy),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.tabularNumbers())
    }
}

@Composable
private fun RepTable(phases: List<PhaseSummary>, toggle: SpeedToggle, locale: Locale, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val header = MaterialTheme.typography.labelMedium
    val body = MaterialTheme.typography.bodyMedium.tabularNumbers()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Cell(stringResource(R.string.intervals_header_rep), REP_WEIGHT, header, muted, align = TextAlign.Start)
            Cell(stringResource(R.string.intervals_header_time), TIME_WEIGHT, header, muted)
            Cell(stringResource(R.string.intervals_header_distance), DISTANCE_WEIGHT, header, muted)
            Cell(
                text = stringResource(if (toggle.showSpeed) R.string.intervals_header_speed else R.string.intervals_header_pace),
                weight = PACE_WEIGHT,
                style = header,
                color = muted,
                modifier = Modifier.speedToggle(toggle),
            )
            Cell(stringResource(R.string.intervals_header_hr), HR_WEIGHT, header, muted)
        }
        var rep = 0
        for (phase in phases) {
            val isRep = phase.kind == PhaseKind.WORK
            if (!isRep && phase.kind != PhaseKind.RECOVERY) continue
            val color = if (isRep) Color.Unspecified else muted
            val distance = phase.distanceMeters
            val hr = phase.averageHr?.let { average ->
                phase.maxHr?.let { max -> stringResource(R.string.intervals_hr_pair, average, max) }
            }
            Row(Modifier.fillMaxWidth()) {
                Cell(
                    if (isRep) (++rep).toString() else stringResource(R.string.intervals_recovery_row),
                    REP_WEIGHT,
                    body,
                    color,
                    align = TextAlign.Start,
                )
                Cell(Formats.duration(phase.durationMillis), TIME_WEIGHT, body, color)
                Cell(distance?.let { Formats.kilometres(it, locale) } ?: DASH, DISTANCE_WEIGHT, body, color)
                Cell(
                    text = distance?.let { paceValue(phase.durationMillis, it, toggle.showSpeed, locale) } ?: DASH,
                    weight = PACE_WEIGHT,
                    style = body,
                    color = color,
                    modifier = Modifier.speedToggle(toggle),
                )
                Cell(hr ?: DASH, HR_WEIGHT, body, color)
            }
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.End,
) {
    Text(text = text, style = style, color = color, textAlign = align, maxLines = 1, modifier = modifier.weight(weight))
}

private const val REP_WEIGHT = 0.6f
private const val TIME_WEIGHT = 1f
private const val DISTANCE_WEIGHT = 1f
private const val PACE_WEIGHT = 1.1f
private const val HR_WEIGHT = 1.4f
internal const val DASH = "–"
