package app.pacinglab.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.icon.PacingLabIcons

@Composable
internal fun SwitchItem(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) }),
    )
}

/** A setting with a few values, shown as chips under its title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceItem(
    title: String,
    summary: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in options) {
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
internal fun SettingsItem(title: String, summary: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}
