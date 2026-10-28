package app.pacinglab.feature.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import java.time.LocalDate
import java.util.Locale

/** The span tabs and, under them, the period shown with arrows to step through history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodControls(period: Period, today: LocalDate, onPeriod: (Period) -> Unit, locale: Locale, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Span.entries.forEachIndexed { i, span ->
                SegmentedButton(
                    selected = period.span == span,
                    onClick = { onPeriod(Period(span, today)) },
                    shape = SegmentedButtonDefaults.itemShape(i, Span.entries.size),
                    icon = {},
                ) { Text(stringResource(span.label), maxLines = 1) }
            }
        }
        PeriodStepper(
            label = period.label(locale),
            onPrevious = { onPeriod(period.previous()) },
            onNext = if (period.isCurrent(today)) null else ({ onPeriod(period.next(today)) }),
        )
    }
}

/** A label between arrows to the previous and next period or day; the next arrow is off at the present. */
@Composable
internal fun PeriodStepper(label: String, onPrevious: () -> Unit, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(painterResource(PacingLabIcons.ChevronLeft), contentDescription = stringResource(R.string.period_previous))
        }
        IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
            Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = stringResource(R.string.period_next))
        }
    }
}
