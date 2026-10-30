package app.pacinglab.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pacinglab.core.designsystem.icon.PacingLabIcons

/**
 * The settings list: one row per group of settings ([SettingsSection]), each opening a page of its own,
 * and About at the end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenSection: (SettingsSection) -> Unit, onOpenAbout: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(SettingsSection.entries, key = { it.name }) { section ->
                SettingsItem(
                    title = stringResource(section.title),
                    summary = stringResource(section.summary),
                    icon = section.icon,
                    onClick = { onOpenSection(section) },
                )
            }
            item(key = "about") {
                SettingsItem(
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.settings_about_summary),
                    icon = PacingLabIcons.Info,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
