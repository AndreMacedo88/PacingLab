package app.pacinglab.feature.trends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.load.Vo2MaxDirection
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.StatusColors
import app.pacinglab.core.designsystem.theme.tabularNumbers
import java.util.Locale
import kotlin.math.roundToInt
import app.pacinglab.core.designsystem.R as DesignR

/**
 * The training status in a word with the evidence behind it: this week's load against the usual, the
 * VO2max trend, fitness over two weeks and HRV against its normal range.
 */
@Composable
internal fun TrainingStatusCard(status: TrainingStatus?, locale: Locale, modifier: Modifier = Modifier) {
    TrendCard(
        title = stringResource(DesignR.string.training_status_title),
        info = stringResource(R.string.status_info),
        modifier = modifier,
    ) {
        if (status == null) {
            Text(
                stringResource(R.string.status_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@TrendCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(status.kind.icon()), contentDescription = null, tint = status.kind.tint(), modifier = Modifier.size(28.dp))
            Text(stringResource(status.kind.label()), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(status.kind.detail()), style = MaterialTheme.typography.bodyMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Reason(
                stringResource(R.string.status_load),
                stringResource(R.string.status_load_value, "%.2f".format(locale, status.loadRatio)),
            )
            Reason(
                stringResource(R.string.status_vo2max),
                status.vo2maxChange?.let { change ->
                    stringResource(
                        when (status.vo2maxDirection) {
                            Vo2MaxDirection.RISING -> R.string.status_vo2max_rising
                            Vo2MaxDirection.FALLING -> R.string.status_vo2max_falling
                            else -> R.string.status_vo2max_steady
                        },
                        "%+.1f".format(locale, change),
                    )
                } ?: stringResource(R.string.status_vo2max_unknown),
            )
            Reason(
                stringResource(R.string.status_fitness),
                stringResource(
                    R.string.status_fitness_value,
                    status.fitness.roundToInt(),
                    "%+d".format(locale, status.fitnessChange.roundToInt()),
                ),
            )
            Reason(
                stringResource(R.string.status_hrv),
                stringResource(
                    when (status.hrvBelowNormal) {
                        true -> R.string.status_hrv_below
                        false -> R.string.status_hrv_normal
                        null -> R.string.status_hrv_unknown
                    },
                ),
            )
        }
    }
}

@Composable
private fun Reason(name: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}

private fun TrainingStatusKind.label(): Int = when (this) {
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

private fun TrainingStatusKind.tint() = when (this) {
    TrainingStatusKind.PRODUCTIVE, TrainingStatusKind.PEAKING, TrainingStatusKind.MAINTAINING, TrainingStatusKind.RECOVERY ->
        StatusColors.Good

    TrainingStatusKind.UNPRODUCTIVE, TrainingStatusKind.DETRAINING -> StatusColors.Warning

    TrainingStatusKind.OVERREACHING, TrainingStatusKind.STRAINED -> StatusColors.Serious
}
