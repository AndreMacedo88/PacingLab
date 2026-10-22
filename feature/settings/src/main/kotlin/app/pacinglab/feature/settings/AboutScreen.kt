package app.pacinglab.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pacinglab.core.designsystem.icon.PacingLabIcons

/**
 * Version, licence, source code, privacy policy and the attributions the map data and the libraries
 * ask for. Links open in the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier, version: String? = null) {
    val context = LocalContext.current
    val shown = version ?: remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    val uris = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.about_app), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.about_version, shown),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.about_purpose),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item {
                Link(stringResource(R.string.about_source), stringResource(R.string.about_licence), onClick = { uris.openUri(SOURCE_URL) })
            }
            item {
                Link(stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_summary), onClick = {
                    uris.openUri(PRIVACY_URL)
                })
            }
            item {
                Link(stringResource(R.string.about_maps), stringResource(R.string.about_maps_summary), onClick = {
                    uris.openUri(OSM_COPYRIGHT_URL)
                })
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_libraries)) },
                    supportingContent = { Text(stringResource(R.string.about_libraries_summary)) },
                )
            }
        }
    }
}

@Composable
private fun Link(title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(painterResource(PacingLabIcons.OpenInNew), contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}

private const val SOURCE_URL = "https://github.com/AndreMacedo88/PacingLab"
private const val PRIVACY_URL = "https://andremacedo88.github.io/PacingLab/privacy.html"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
