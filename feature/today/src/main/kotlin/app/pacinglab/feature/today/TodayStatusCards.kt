package app.pacinglab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.recovery.VitalSign
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.RangePill
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.designsystem.R as DesignR

/** Last night's vitals as the Sleep screen judges them, from the ranges Today already holds. */
internal fun TodayUiState.Loaded.vitals(): Vitals? {
    val night = lastNight() ?: return null
    return Vitals(today, Vitals.METRICS.map { VitalSign(it, it.value(night), rangeTonight(it)) })
}

/**
 * Last night's five overnight measures in one card, each against the athlete's range, with a warning when
 * two or more are outside at once (Apple's Vitals rule). SpO2 and skin temperature appear once the Google
 * Health API supplies them.
 */
@Composable
internal fun VitalsTodayCard(vitals: Vitals?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    TodayWideCard(stringResource(R.string.vitals_title), stringResource(R.string.vitals_info), onClick, modifier) {
        val shown = vitals?.signs?.filter { it.value != null }.orEmpty()
        if (vitals == null || shown.isEmpty()) {
            Text(
                stringResource(R.string.tile_no_night),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TodayWideCard
        }
        if (vitals.judged > 0) VitalsVerdict(vitals)
        for (sign in shown) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(sign.metric.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    sign.metric.format(sign.value!!, locale),
                    style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                    modifier = Modifier.padding(end = 12.dp),
                )
                RangePill(sign.standing)
            }
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

/** The training status in a word, what it means, and the load ratio behind it. Opens Trends. */
@Composable
internal fun TrainingStatusTodayCard(status: TrainingStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TodayWideCard(stringResource(DesignR.string.training_status_title), stringResource(R.string.training_status_info), onClick, modifier) {
        if (status == null) {
            Text(
                stringResource(R.string.training_status_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TodayWideCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(status.kind.icon()),
                contentDescription = null,
                tint = status.kind.tint(),
                modifier = Modifier.size(24.dp),
            )
            Text(stringResource(status.kind.label()), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(status.kind.detail()), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TodayWideCard(
    title: String,
    info: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = title, info = info)
            content()
        }
    }
}

internal fun TrainingStatusKind.label(): Int = when (this) {
    TrainingStatusKind.PRODUCTIVE -> DesignR.string.training_status_productive
    TrainingStatusKind.MAINTAINING -> DesignR.string.training_status_maintaining
    TrainingStatusKind.PEAKING -> DesignR.string.training_status_peaking
    TrainingStatusKind.RECOVERY -> DesignR.string.training_status_recovery
    TrainingStatusKind.UNPRODUCTIVE -> DesignR.string.training_status_unproductive
    TrainingStatusKind.DETRAINING -> DesignR.string.training_status_detraining
    TrainingStatusKind.OVERREACHING -> DesignR.string.training_status_overreaching
    TrainingStatusKind.STRAINED -> DesignR.string.training_status_strained
}

private fun TrainingStatusKind.detail(): Int = when (this) {
    TrainingStatusKind.PRODUCTIVE -> DesignR.string.training_status_productive_detail
    TrainingStatusKind.MAINTAINING -> DesignR.string.training_status_maintaining_detail
    TrainingStatusKind.PEAKING -> DesignR.string.training_status_peaking_detail
    TrainingStatusKind.RECOVERY -> DesignR.string.training_status_recovery_detail
    TrainingStatusKind.UNPRODUCTIVE -> DesignR.string.training_status_unproductive_detail
    TrainingStatusKind.DETRAINING -> DesignR.string.training_status_detraining_detail
    TrainingStatusKind.OVERREACHING -> DesignR.string.training_status_overreaching_detail
    TrainingStatusKind.STRAINED -> DesignR.string.training_status_strained_detail
}

private fun TrainingStatusKind.icon(): Int = when (this) {
    TrainingStatusKind.PRODUCTIVE, TrainingStatusKind.PEAKING -> PacingLabIcons.ArrowUp
    TrainingStatusKind.MAINTAINING, TrainingStatusKind.RECOVERY -> PacingLabIcons.CheckCircle
    TrainingStatusKind.UNPRODUCTIVE, TrainingStatusKind.DETRAINING -> PacingLabIcons.ArrowDown
    TrainingStatusKind.OVERREACHING, TrainingStatusKind.STRAINED -> PacingLabIcons.Warning
}

@Composable
private fun TrainingStatusKind.tint() = when (this) {
    TrainingStatusKind.PRODUCTIVE, TrainingStatusKind.PEAKING, TrainingStatusKind.MAINTAINING, TrainingStatusKind.RECOVERY ->
        StatusColors.Good

    TrainingStatusKind.UNPRODUCTIVE, TrainingStatusKind.DETRAINING -> StatusColors.Warning

    TrainingStatusKind.OVERREACHING, TrainingStatusKind.STRAINED -> StatusColors.Serious
}
