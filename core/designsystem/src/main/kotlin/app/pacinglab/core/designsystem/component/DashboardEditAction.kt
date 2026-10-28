package app.pacinglab.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons

/** The app bar action that starts arranging a dashboard's cards, and finishes it. */
@Composable
fun DashboardEditAction(editing: Boolean, onEditingChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    if (editing) {
        TextButton(onClick = { onEditingChange(false) }, modifier = modifier) { Text(stringResource(R.string.dashboard_done)) }
    } else {
        IconButton(onClick = { onEditingChange(true) }, modifier = modifier) {
            Icon(painterResource(PacingLabIcons.Edit), contentDescription = stringResource(R.string.dashboard_edit))
        }
    }
}
