package app.pacinglab.feature.workouts.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.data.sync.SyncStatus
import app.pacinglab.core.designsystem.component.icon
import app.pacinglab.core.designsystem.component.label
import app.pacinglab.core.designsystem.format.Formats
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.model.Sport
import app.pacinglab.feature.workouts.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutsRoute(
    onOpenSession: (String) -> Unit,
    onOpenDataSources: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutsScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenSession = onOpenSession,
        onOpenDataSources = onOpenDataSources,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    state: WorkoutsUiState,
    onRefresh: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenDataSources: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.workouts_title)) }) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.sync == SyncStatus.Updating,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                val sync = state.sync
                if (sync is SyncStatus.Importing) {
                    item(key = "importing") { ImportingCard(sync, locale, zoneId) }
                }
                val outcome = state.lastOutcome
                when {
                    outcome?.rateLimited == true && sync == SyncStatus.Idle -> item(key = "paused") {
                        InfoCard(stringResource(R.string.workouts_paused))
                    }

                    outcome?.noAccess == true -> item(key = "no-access") {
                        NoticeCard(
                            stringResource(R.string.workouts_no_access),
                            null,
                            stringResource(R.string.workouts_set_up_access),
                            onOpenDataSources,
                        )
                    }

                    outcome != null && outcome.errors.isNotEmpty() && sync == SyncStatus.Idle -> item(key = "failed") {
                        NoticeCard(
                            stringResource(R.string.workouts_sync_failed),
                            outcome.errors.first(),
                            stringResource(R.string.workouts_try_again),
                            onRefresh,
                        )
                    }
                }
                if (!state.loading && state.sessions.isEmpty() && sync == SyncStatus.Idle && outcome?.noAccess != true) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.workouts_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                val byMonth = state.sessions.groupBy { it.start.atZone(it.zoneOffset ?: zoneId).withDayOfMonth(1).toLocalDate() }
                byMonth.forEach { (month, sessions) ->
                    item(key = "month-$month") {
                        Text(
                            text = monthFormat.format(month).replaceFirstChar { it.titlecase(locale) },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
                        )
                    }
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(session, locale, zoneId, onClick = { onOpenSession(session.id) })
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportingCard(status: SyncStatus.Importing, locale: Locale, zoneId: ZoneId, modifier: Modifier = Modifier) {
    val reached = remember(status.reached, locale) {
        status.reached?.let { DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zoneId).format(it) }
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.workouts_importing), style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
            if (reached != null) {
                Text(
                    text = stringResource(R.string.workouts_importing_reached, reached),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun NoticeCard(title: String, detail: String?, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SessionRow(session: SessionItem, locale: Locale, zoneId: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val zone = session.zoneOffset ?: zoneId
    val title = session.titles.joinToString(" + ").ifEmpty { stringResource(session.sport.label()) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(session.sport.icon()),
                contentDescription = stringResource(session.sport.label()),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = dateFormat.format(session.start.atZone(zone)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = metricsLine(session, locale),
                style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (session.parts > 1) {
                Text(
                    text = pluralStringResource(R.plurals.session_parts, session.parts, session.parts),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun metricsLine(session: SessionItem, locale: Locale): String {
    val parts = mutableListOf<String>()
    // Step-based distance during strength training is meaningless.
    session.distanceMeters?.takeIf { it >= MIN_SHOWN_DISTANCE && session.sport != Sport.STRENGTH }?.let {
        parts += stringResource(R.string.unit_km, Formats.kilometres(it, locale))
    }
    parts += Formats.duration(session.movingMillis)
    if (session.sport == Sport.RUN || session.sport == Sport.WALK) {
        session.distanceMeters?.let { Formats.pacePerKm(session.movingMillis, it) }?.let {
            parts += stringResource(R.string.unit_pace, it)
        }
    }
    session.averageHr?.let { parts += stringResource(R.string.unit_bpm, it) }
    return parts.joinToString("  ·  ")
}

private const val MIN_SHOWN_DISTANCE = 50.0
