package app.pacinglab.feature.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.DistributionModel
import app.pacinglab.core.analytics.load.DomainShare
import app.pacinglab.core.analytics.load.FocusStanding
import app.pacinglab.core.analytics.load.IntensityFocus
import app.pacinglab.core.designsystem.component.StatusPill
import app.pacinglab.core.designsystem.component.zoneName
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.theme.zoneColors
import app.pacinglab.core.model.ZoneModel
import kotlin.math.roundToInt

/**
 * Four weeks of endurance training split across the three intensity domains, each against the band
 * successful endurance athletes train in, with the shape of the distribution and its biggest gap.
 */
@Composable
internal fun IntensityFocusCard(focus: IntensityFocus?, modifier: Modifier = Modifier) {
    TrendCard(title = stringResource(R.string.focus_title), info = stringResource(R.string.focus_info), modifier = modifier) {
        if (focus == null) {
            Text(
                stringResource(R.string.focus_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TrendCard
        }
        val gap = focus.mainGap
        Text(
            text = if (gap == null) {
                stringResource(R.string.focus_balanced)
            } else {
                stringResource(
                    if (gap.standing == FocusStanding.SHORT) R.string.focus_short else R.string.focus_over,
                    stringResource(ZoneModel.POLARIZED_THREE.zoneName(gap.domain.ordinal)),
                )
            },
            style = MaterialTheme.typography.titleMedium,
        )
        focus.model?.let {
            Text(
                stringResource(it.label()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val colors = zoneColors(3)
        for (share in focus.shares) {
            DomainRow(share, colors[share.domain.ordinal])
        }
    }
}

@Composable
private fun DomainRow(share: DomainShare, color: Color, modifier: Modifier = Modifier) {
    val name = stringResource(ZoneModel.POLARIZED_THREE.zoneName(share.domain.ordinal))
    val percent = (share.share * 100).roundToInt()
    val target =
        stringResource(R.string.focus_target, (share.target.start * 100).roundToInt(), (share.target.endInclusive * 100).roundToInt())
    val track = MaterialTheme.colorScheme.surfaceVariant
    val band = color.copy(alpha = BAND_ALPHA)
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name $percent %, $target" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.focus_share, name, percent, Formats.hoursMinutes(share.millis)),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                modifier = Modifier.weight(1f),
            )
            StandingPill(share.standing)
        }
        // As the sleep quality bars: the track is 0-100 %, the target band is shaded in the zone's colour and
        // the share is a dot in that colour, so the band stays visible wherever the share lies.
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val height = 10.dp.toPx()
            val top = (size.height - height) / 2
            val radius = CornerRadius(height / 2)
            fun x(share: Double) = (share.coerceIn(0.0, 1.0) * size.width).toFloat()
            drawRoundRect(track, Offset(0f, top), Size(size.width, height), radius)
            drawRoundRect(
                band,
                Offset(x(share.target.start), top),
                Size(x(share.target.endInclusive) - x(share.target.start), height),
                radius,
            )
            val center = Offset(x(share.share).coerceIn(8.dp.toPx(), size.width - 8.dp.toPx()), size.height / 2)
            drawCircle(ring, 8.dp.toPx(), center)
            drawCircle(color, 6.dp.toPx(), center)
        }
        Text(target, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StandingPill(standing: FocusStanding) {
    when (standing) {
        FocusStanding.IN_BAND -> StatusPill(stringResource(R.string.focus_in_band), StatusColors.Good, PacingLabIcons.CheckCircle)
        FocusStanding.SHORT -> StatusPill(stringResource(R.string.focus_shortage), StatusColors.Warning, PacingLabIcons.ArrowDown)
        FocusStanding.OVER -> StatusPill(stringResource(R.string.focus_excess), StatusColors.Warning, PacingLabIcons.ArrowUp)
    }
}

private const val BAND_ALPHA = 0.35f

private fun DistributionModel.label(): Int = when (this) {
    DistributionModel.POLARIZED -> R.string.focus_model_polarized
    DistributionModel.PYRAMIDAL -> R.string.focus_model_pyramidal
    DistributionModel.THRESHOLD -> R.string.focus_model_threshold
    DistributionModel.HIGH_INTENSITY -> R.string.focus_model_high
}
