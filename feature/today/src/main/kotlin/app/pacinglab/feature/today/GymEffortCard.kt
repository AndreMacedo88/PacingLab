package app.pacinglab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.EffortPicker
import app.pacinglab.core.designsystem.format.Formats
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Asks once how hard the latest gym session felt; the card goes away with the answer. Heart rate
 * understates lifting, so the rating is what gives a gym session its training load.
 */
@Composable
internal fun GymEffortCard(gym: GymToRate, today: LocalDate, zoneId: ZoneId, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val start = gym.start.atZone(gym.zoneOffset ?: zoneId)
    val day = stringResource(if (start.toLocalDate() == today) R.string.day_today else R.string.day_yesterday)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.gym_effort_title), info = stringResource(R.string.gym_effort_info))
            Text(
                text = stringResource(R.string.gym_effort_when, day, time.format(start), Formats.duration(gym.durationMillis)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EffortPicker(rating = null, onRate = onRate)
        }
    }
}
