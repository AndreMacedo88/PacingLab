package app.pacinglab.feature.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.recovery.FeelingAgreement
import app.pacinglab.core.designsystem.theme.chartColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale
import kotlin.math.floor

/**
 * Whether the sleep score follows how rested the athlete says they feel: a dot per rated morning, its
 * score across and the answer up the side, with each answer's median score marked. The rank correlation
 * joins from [FeelingAgreement.MIN_MORNINGS] mornings; until then the card counts towards it.
 */
@Composable
internal fun FeelingCard(agreement: FeelingAgreement, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val count = agreement.mornings.size
    SleepCard(stringResource(R.string.feeling_title), stringResource(R.string.feeling_info), modifier) {
        val rho = agreement.rho
        when {
            rho != null -> Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(locale, "%.2f", rho).replace('-', '−'),
                    style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(agreement.strength!!.label()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = pluralStringResource(R.plurals.feeling_over, count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            count >= FeelingAgreement.MIN_MORNINGS -> Text(
                text = stringResource(R.string.feeling_constant),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> Column {
                Text(
                    text = pluralStringResource(R.plurals.feeling_progress, count, count, FeelingAgreement.MIN_MORNINGS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { count.toFloat() / FeelingAgreement.MIN_MORNINGS },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    drawStopIndicator = {},
                )
            }
        }
        if (count > 0) FeelingStrip(agreement, locale)
    }
}

@Composable
private fun FeelingStrip(agreement: FeelingAgreement, locale: Locale, modifier: Modifier = Modifier) {
    val dot = chartColors().series[0]
    val grid = chartColors().grid
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val median = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val style = MaterialTheme.typography.labelSmall.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val words = stringArrayResource(R.array.sleep_rested_words).map { word -> word.replaceFirstChar { it.titlecase(locale) } }
    val description = pluralStringResource(R.plurals.feeling_description, agreement.mornings.size, agreement.mornings.size)
    val low = minOf(LOWEST_AXIS, floor(agreement.mornings.minOf { it.score } / 10.0).toInt() * 10)
    Canvas(modifier.fillMaxWidth().height(ROW_HEIGHT * RATINGS + AXIS_HEIGHT).semantics { contentDescription = description }) {
        val labels = words.map { measurer.measure(it, style) }
        val left = labels.maxOf { it.size.width } + 12.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val rowHeight = ROW_HEIGHT.toPx()
        fun x(score: Number) = left + (score.toFloat() - low) / (100 - low) * (right - left)
        fun y(rating: Int) = (RATINGS - rating + 0.5f) * rowHeight

        for (score in low..100 step 10) {
            drawLine(grid, Offset(x(score), 0f), Offset(x(score), RATINGS * rowHeight), 1.dp.toPx())
            val label = measurer.measure(score.toString(), style)
            drawText(label, topLeft = Offset(x(score) - label.size.width / 2f, RATINGS * rowHeight + 4.dp.toPx()))
        }
        for (rating in 1..RATINGS) {
            val label = labels[rating - 1]
            drawText(label, topLeft = Offset(0f, y(rating) - label.size.height / 2f))
            val scores = agreement.mornings.filter { it.rested == rating }.map { it.score }.sorted()
            for (score in scores) {
                // A ring in the card's colour keeps overlapping dots apart.
                drawCircle(ring, DOT_RADIUS.toPx() + 1.5.dp.toPx(), Offset(x(score), y(rating)))
                drawCircle(dot.copy(alpha = DOT_ALPHA), DOT_RADIUS.toPx(), Offset(x(score), y(rating)))
            }
            if (scores.size >= 2) {
                val middle = if (scores.size % 2 ==
                    1
                ) {
                    scores[scores.size / 2].toFloat()
                } else {
                    (scores[scores.size / 2 - 1] + scores[scores.size / 2]) / 2f
                }
                val half = rowHeight * 0.36f
                drawLine(median, Offset(x(middle), y(rating) - half), Offset(x(middle), y(rating) + half), 2.dp.toPx())
            }
        }
    }
}

private fun FeelingAgreement.Strength.label(): Int = when (this) {
    FeelingAgreement.Strength.OPPOSITE -> R.string.feeling_opposite
    FeelingAgreement.Strength.NONE -> R.string.feeling_none
    FeelingAgreement.Strength.WEAK -> R.string.feeling_weak
    FeelingAgreement.Strength.MODERATE -> R.string.feeling_moderate
    FeelingAgreement.Strength.STRONG -> R.string.feeling_strong
}

private const val RATINGS = 5
private const val LOWEST_AXIS = 50
private const val DOT_ALPHA = 0.75f
private val ROW_HEIGHT = 28.dp
private val AXIS_HEIGHT = 20.dp
private val DOT_RADIUS = 5.dp
