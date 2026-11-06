package app.pacinglab.feature.sleep

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.ui.AlertnessSummary
import java.time.LocalDate
import java.time.ZoneId
import app.pacinglab.core.ui.R as UiR

/**
 * How alert the athlete can expect to be through the day after the night shown ([AlertnessSummary]);
 * the reaction test is offered on today's morning only. Opens the alertness page.
 */
@Composable
internal fun AlertnessCard(
    history: AlertnessHistory?,
    morning: LocalDate,
    zone: ZoneId,
    onOpen: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SleepCard(
        title = stringResource(UiR.string.alertness_title),
        info = stringResource(UiR.string.alertness_info),
        modifier = modifier,
        advanced = stringResource(UiR.string.alertness_advanced),
        onClick = onOpen,
    ) {
        AlertnessSummary(history, morning, zone, onTest = onTest.takeIf { history?.today == morning })
    }
}
