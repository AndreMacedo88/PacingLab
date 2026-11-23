package app.pacinglab.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.EffortPicker
import app.pacinglab.core.designsystem.component.InfoButton
import app.pacinglab.core.designsystem.component.RestedPicker
import app.pacinglab.core.designsystem.component.dashboardCardColors
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import app.pacinglab.core.ui.R as UiR

/**
 * What waits for the athlete today, first on the screen so that it is not forgotten. Each item leaves the card
 * once done, and the card leaves with the last one.
 *
 * @property rested the morning question is open (or was answered a moment ago: [rating] shows the answer).
 * @property gym the newest gym session of today or yesterday, while it has no effort rating.
 * @property testDue a reaction test is due, to keep tuning alertness to the athlete.
 * @property valuesMissing no maximal heart rate yet, so load, form and zones cannot be worked out.
 */
data class TodoItems(
    val rested: Boolean = false,
    val rating: Int? = null,
    val gym: GymToRate? = null,
    val testDue: Boolean = false,
    val valuesMissing: Boolean = false,
) {
    val any: Boolean get() = rested || gym != null || testDue || valuesMissing
}

@Composable
internal fun TodoCard(
    items: TodoItems,
    today: LocalDate,
    zoneId: ZoneId,
    onRate: (Int) -> Unit,
    onRateGym: (Int) -> Unit,
    onTest: () -> Unit,
    onOpenPhysiology: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = dashboardCardColors()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.todo_title))
            var first = true
            val divider: @Composable () -> Unit = {
                if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                first = false
            }
            if (items.rested) {
                divider()
                Question(stringResource(R.string.rested_title), stringResource(R.string.rested_info))
                RestedPicker(items.rating, onRate)
            }
            items.gym?.let { gym ->
                divider()
                GymQuestion(gym, today, zoneId)
                EffortPicker(rating = null, onRate = onRateGym)
            }
            if (items.testDue) {
                divider()
                Errand(stringResource(R.string.todo_test), stringResource(R.string.todo_test_detail), onTest)
            }
            if (items.valuesMissing) {
                divider()
                Errand(stringResource(R.string.today_set_values_action), stringResource(R.string.today_set_values), onOpenPhysiology)
            }
        }
    }
}

/** A question's line, with the button that says why it is asked. */
@Composable
private fun Question(text: String, info: String, advanced: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        InfoButton(title = text, text = info, advanced = advanced, modifier = Modifier.offset(x = 12.dp))
    }
}

/**
 * How hard the latest gym session felt, asked once; heart rate understates lifting, so the rating is what gives
 * a gym session its training load.
 */
@Composable
private fun GymQuestion(gym: GymToRate, today: LocalDate, zoneId: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val start = gym.start.atZone(gym.zoneOffset ?: zoneId)
    val day = stringResource(if (start.toLocalDate() == today) R.string.day_today else R.string.day_yesterday)
    Column {
        Question(
            stringResource(R.string.gym_effort_title),
            stringResource(R.string.gym_effort_info),
            advanced = stringResource(UiR.string.ui_effort_advanced),
        )
        Text(
            text = stringResource(R.string.gym_effort_when, day, time.format(start), Formats.duration(gym.durationMillis)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Something to do elsewhere: a line that opens it. */
@Composable
private fun Errand(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painterResource(PacingLabIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
