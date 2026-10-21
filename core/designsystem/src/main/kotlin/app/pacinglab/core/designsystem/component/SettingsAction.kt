package app.pacinglab.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.designsystem.R
import app.pacinglab.core.designsystem.icon.PacingLabIcons

/** The settings button at the right of a top-level screen's app bar. */
@Composable
fun SettingsAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(PacingLabIcons.Settings), contentDescription = stringResource(R.string.settings_action))
    }
}
