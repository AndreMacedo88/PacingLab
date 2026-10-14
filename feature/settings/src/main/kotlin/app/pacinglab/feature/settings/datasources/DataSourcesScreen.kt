package app.pacinglab.feature.settings.datasources

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.designsystem.theme.tabularNumbers
import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.probe.ProbeProgress
import app.pacinglab.feature.settings.R
import java.io.File
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DataSourcesRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DataSourcesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contract = remember(viewModel) { viewModel.permissionContract }
    val permissionLauncher = rememberLauncherForActivityResult(contract) { viewModel.refresh() }
    // Permissions can change in Health Connect's own settings while the app is in the background.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    DataSourcesScreen(
        state = state,
        exportSessionCount = viewModel.exportSessionCount,
        onBack = onBack,
        onGrantAccess = { permissionLauncher.launch(viewModel.requestedPermissions) },
        onOpenHealthConnect = { context.startActivity(viewModel.healthConnectSettingsIntent) },
        onRunProbe = viewModel::runProbe,
        onExport = viewModel::exportRawSessions,
        onShareFile = context::shareJson,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSourcesScreen(
    state: DataSourcesUiState,
    exportSessionCount: Int,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onRunProbe: () -> Unit,
    onExport: () -> Unit,
    onShareFile: (File) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_sources_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(PacingLabIcons.ArrowBack), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HealthConnectCard(state, onGrantAccess, onOpenHealthConnect)
            }
            if (state.availability == HealthConnectAvailability.AVAILABLE) {
                item { ProbeCard(state.probe, onRunProbe, onShareFile) }
                val probe = state.probe
                if (probe is ProbeState.Finished) {
                    item { TypesCard(probe.highlights.types, zoneId) }
                    if (probe.highlights.sessions.isNotEmpty()) {
                        item { SessionsCard(probe.highlights.sessions, zoneId) }
                    }
                }
                item { ExportCard(state.export, exportSessionCount, onExport, onShareFile) }
            }
        }
    }
}

@Composable
private fun HealthConnectCard(state: DataSourcesUiState, onGrantAccess: () -> Unit, onOpenHealthConnect: () -> Unit) {
    SectionCard(title = stringResource(R.string.health_connect_title)) {
        when (state.availability) {
            null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.health_connect_checking), style = MaterialTheme.typography.bodyMedium)
                }
            }

            HealthConnectAvailability.UNAVAILABLE -> {
                StatusLine(PacingLabIcons.Error, MaterialTheme.colorScheme.error, stringResource(R.string.health_connect_unavailable))
            }

            HealthConnectAvailability.UPDATE_REQUIRED -> {
                StatusLine(
                    PacingLabIcons.Warning,
                    MaterialTheme.colorScheme.error,
                    stringResource(R.string.health_connect_update_required),
                )
            }

            HealthConnectAvailability.AVAILABLE -> {
                if (state.allGranted) {
                    StatusLine(
                        PacingLabIcons.CheckCircle,
                        MaterialTheme.colorScheme.primary,
                        stringResource(R.string.health_connect_all_granted),
                    )
                } else {
                    StatusLine(
                        PacingLabIcons.Warning,
                        MaterialTheme.colorScheme.tertiary,
                        stringResource(R.string.health_connect_access, state.grantedCount, state.requestedCount),
                    )
                }
                state.features?.let { features ->
                    Text(
                        text = stringResource(R.string.health_connect_history, supportLabel(features.readHistory)) + "\n" +
                            stringResource(R.string.health_connect_background, supportLabel(features.readInBackground)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.allGranted) {
                        Button(onClick = onGrantAccess) { Text(stringResource(R.string.grant_access)) }
                    }
                    OutlinedButton(onClick = onOpenHealthConnect) { Text(stringResource(R.string.open_health_connect)) }
                }
            }
        }
    }
}

@Composable
private fun ProbeCard(probe: ProbeState, onRunProbe: () -> Unit, onShareFile: (File) -> Unit) {
    SectionCard(title = stringResource(R.string.probe_title)) {
        Text(
            text = stringResource(R.string.probe_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (probe) {
            ProbeState.Idle -> {
                Button(onClick = onRunProbe) { Text(stringResource(R.string.probe_run)) }
            }

            is ProbeState.Running -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(progressLabel(probe.progress), style = MaterialTheme.typography.bodySmall)
            }

            is ProbeState.Finished -> {
                if (probe.highlights.errorCount > 0) {
                    StatusLine(
                        PacingLabIcons.Warning,
                        MaterialTheme.colorScheme.tertiary,
                        pluralStringResource(R.plurals.probe_errors, probe.highlights.errorCount, probe.highlights.errorCount),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onShareFile(probe.file) }) { Text(stringResource(R.string.probe_share)) }
                    OutlinedButton(onClick = onRunProbe) { Text(stringResource(R.string.probe_run_again)) }
                }
            }

            is ProbeState.Failed -> {
                StatusLine(PacingLabIcons.Error, MaterialTheme.colorScheme.error, stringResource(R.string.probe_failed, probe.message))
                OutlinedButton(onClick = onRunProbe) { Text(stringResource(R.string.probe_run_again)) }
            }
        }
    }
}

@Composable
private fun TypesCard(types: List<ProbeHighlights.TypeRow>, zoneId: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val dates = remember(locale, zoneId) { DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zoneId) }
    SectionCard(title = stringResource(R.string.probe_section_types)) {
        types.forEachIndexed { index, type ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val trailing = when {
                !type.granted -> stringResource(R.string.probe_no_access)
                type.failed -> stringResource(R.string.probe_type_error)
                else -> (type.records ?: 0).let { pluralStringResource(R.plurals.probe_records, it, numbers.format(it)) }
            }
            val supporting = when {
                type.earliest == null -> {
                    stringResource(R.string.probe_no_data)
                }

                else -> {
                    listOfNotNull(
                        stringResource(R.string.probe_since, dates.format(type.earliest)),
                        type.mainOrigin,
                    ).joinToString(" · ")
                }
            }
            DetailRow(
                headline = stringResource(recordTypeLabel(type.name)),
                supporting = supporting.takeIf { type.granted && !type.failed },
                trailing = trailing,
            )
        }
    }
}

@Composable
private fun SessionsCard(sessions: List<ProbeHighlights.SessionRow>, zoneId: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val dates = remember(locale, zoneId) { DateTimeFormatter.ofPattern("EEE d MMM", locale).withZone(zoneId) }
    SectionCard(title = stringResource(R.string.probe_section_sessions)) {
        sessions.forEachIndexed { index, session ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val heartRate = session.heartRateGapSeconds?.let { stringResource(R.string.probe_hr_every, formatSeconds(it, locale)) }
                ?: stringResource(R.string.probe_hr_missing)
            val route = when {
                session.routePoints != null -> stringResource(R.string.probe_route_points, numbers.format(session.routePoints))
                session.routeStatus == "consent_required" -> stringResource(R.string.probe_route_consent)
                else -> stringResource(R.string.probe_route_none)
            }
            DetailRow(
                headline = stringResource(
                    R.string.probe_session_title,
                    exerciseLabel(session.typeName),
                    dates.format(session.start),
                    session.durationMinutes,
                ),
                supporting = listOf(
                    heartRate,
                    route,
                    pluralStringResource(R.plurals.probe_laps, session.laps, session.laps),
                ).joinToString(" · "),
                trailing = null,
            )
        }
    }
}

@Composable
private fun ExportCard(export: ExportState, sessionCount: Int, onExport: () -> Unit, onShareFile: (File) -> Unit) {
    SectionCard(title = stringResource(R.string.export_title)) {
        Text(
            text = pluralStringResource(R.plurals.export_description, sessionCount, sessionCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (export) {
            ExportState.Idle -> {
                OutlinedButton(onClick = onExport) { Text(stringResource(R.string.export_run)) }
            }

            ExportState.Running -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.export_running), style = MaterialTheme.typography.bodySmall)
            }

            is ExportState.Finished -> {
                StatusLine(
                    PacingLabIcons.CheckCircle,
                    MaterialTheme.colorScheme.primary,
                    pluralStringResource(R.plurals.export_done, export.sessionCount, export.sessionCount),
                )
                OutlinedButton(onClick = { onShareFile(export.file) }) { Text(stringResource(R.string.export_share)) }
            }

            is ExportState.Failed -> {
                StatusLine(PacingLabIcons.Error, MaterialTheme.colorScheme.error, stringResource(R.string.export_failed, export.message))
                OutlinedButton(onClick = onExport) { Text(stringResource(R.string.export_run)) }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusLine(@DrawableRes icon: Int, tint: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailRow(headline: String, supporting: String?, trailing: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun supportLabel(supported: Boolean): String =
    stringResource(if (supported) R.string.feature_supported else R.string.feature_unsupported)

@Composable
private fun progressLabel(progress: ProbeProgress?): String = when (progress?.stage) {
    null, ProbeProgress.Stage.PERMISSIONS -> {
        stringResource(R.string.probe_stage_permissions)
    }

    ProbeProgress.Stage.RECORD_TYPES -> {
        stringResource(
            R.string.probe_stage_record_types,
            progress.recordType?.let { stringResource(recordTypeLabel(it.removeSuffix("Record"))) }.orEmpty(),
            progress.current,
            progress.total,
        )
    }

    ProbeProgress.Stage.SESSIONS -> {
        stringResource(R.string.probe_stage_sessions, progress.current, progress.total)
    }

    ProbeProgress.Stage.DAILY_VALUES -> {
        stringResource(R.string.probe_stage_daily_values)
    }

    ProbeProgress.Stage.DONE -> {
        stringResource(R.string.probe_stage_done)
    }
}

@StringRes
private fun recordTypeLabel(name: String): Int = when (name) {
    "ExerciseSession" -> R.string.record_exercise_session
    "HeartRate" -> R.string.record_heart_rate
    "RestingHeartRate" -> R.string.record_resting_heart_rate
    "HeartRateVariabilityRmssd" -> R.string.record_hrv
    "Speed" -> R.string.record_speed
    "Distance" -> R.string.record_distance
    "ElevationGained" -> R.string.record_elevation_gained
    "Steps" -> R.string.record_steps
    "StepsCadence" -> R.string.record_cadence
    "Power" -> R.string.record_power
    "Vo2Max" -> R.string.record_vo2max
    "SleepSession" -> R.string.record_sleep
    "RespiratoryRate" -> R.string.record_respiratory_rate
    "SkinTemperature" -> R.string.record_skin_temperature
    "OxygenSaturation" -> R.string.record_oxygen_saturation
    "ActiveCaloriesBurned" -> R.string.record_active_calories
    "TotalCaloriesBurned" -> R.string.record_total_calories
    "FloorsClimbed" -> R.string.record_floors
    "Weight" -> R.string.record_weight
    "Height" -> R.string.record_height
    else -> R.string.record_other
}

/** "high_intensity_interval_training" becomes "High intensity interval training". */
private fun exerciseLabel(typeName: String): String = typeName.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

private fun formatSeconds(seconds: Double, locale: Locale): String =
    if (seconds % 1.0 == 0.0) seconds.toInt().toString() else String.format(locale, "%.1f", seconds)

private fun Context.shareJson(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, file.name))
}
