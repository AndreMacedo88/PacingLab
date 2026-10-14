package app.pacinglab.feature.settings

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
fun SettingsScreen(onOpenDataSources: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    text = stringResource(R.string.settings_section_data),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.data_sources_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_data_sources_summary)) },
                    leadingContent = { Icon(painterResource(PacingLabIcons.Database), contentDescription = null) },
                    trailingContent = { Icon(painterResource(PacingLabIcons.ChevronRight), contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenDataSources),
                )
            }
        }
    }
}
