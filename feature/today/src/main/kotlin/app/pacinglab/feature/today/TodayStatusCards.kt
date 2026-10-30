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
import app.pacinglab.core.ui.VitalsVerdict
import app.pacinglab.core.ui.detail
import app.pacinglab.core.ui.icon
import app.pacinglab.core.ui.label
import app.pacinglab.core.ui.tint
import app.pacinglab.core.ui.R as UiR

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
        if (vitals.judged > 0) VitalsVerdict(vitals, name = { stringResource(it.label()) })
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

/** The training status in a word, what it means, and the load ratio behind it. Opens Trends. */
@Composable
internal fun TrainingStatusTodayCard(status: TrainingStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TodayWideCard(stringResource(UiR.string.training_status_title), stringResource(R.string.training_status_info), onClick, modifier) {
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
