package app.pacinglab.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.component.CardHeader
import app.pacinglab.core.designsystem.component.RestedPicker

/**
 * "How rested do you feel?" on a scale of 1 to 5, answered once in the morning: the part of sleep health
 * no watch can measure. The card leaves Today once answered; the sleep screen keeps the question for
 * changing the answer.
 */
@Composable
internal fun RestedCard(rating: Int?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CardHeader(title = stringResource(R.string.rested_title), info = stringResource(R.string.rested_info))
            RestedPicker(rating, onRate)
        }
    }
}
