package app.pacinglab.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.icon.PacingLabIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenPhysiology: () -> Unit, onOpenDataSources: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionLabel(stringResource(R.string.settings_section_training)) }
            item {
                SettingsItem(
                    title = stringResource(R.string.physiology_title),
                    summary = stringResource(R.string.settings_physiology_summary),
                    icon = PacingLabIcons.Heart,
                    onClick = onOpenPhysiology,
                )
            }
            item { SectionLabel(stringResource(R.string.settings_section_data)) }
            item {
                SettingsItem(
                    title = stringResource(R.string.data_sources_title),
                    summary = stringResource(R.string.settings_data_sources_summary),
                    icon = PacingLabIcons.Database,
                    onClick = onOpenDataSources,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsItem(title: String, summary: String, @DrawableRes icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = { Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}
