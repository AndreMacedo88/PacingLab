package app.pacinglab.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pacinglab.core.designsystem.R

/**
 * How much history a chart shows. Every chart with a choice of span offers some of these, in this order and
 * with these words, so that "Month" means the same on every screen.
 *
 * @property days the length of the span; null for all the history there is.
 */
enum class Span(val days: Long?, @param:StringRes val label: Int) {
    WEEK(7, R.string.span_week),
    MONTH(30, R.string.span_month),
    THREE_MONTHS(91, R.string.span_three_months),
    YEAR(365, R.string.span_year),
    ALL(null, R.string.span_all),
}

/** A row of span buttons, [options] in the order of [Span], [selected] marked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpanButtons(options: List<Span>, selected: Span, onSelect: (Span) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, span ->
            SegmentedButton(
                selected = span == selected,
                onClick = { onSelect(span) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                // Four spans in a card leave no room for a check mark or the usual padding on a phone, and a long
                // word shrinks a little rather than being cut.
                icon = {},
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    stringResource(span.label),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                )
            }
        }
    }
}
