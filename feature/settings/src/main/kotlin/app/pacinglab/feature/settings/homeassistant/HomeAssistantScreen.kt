package app.pacinglab.feature.settings.homeassistant

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pacinglab.core.data.home.ClimateRole
import app.pacinglab.core.data.home.ConnectResult
import app.pacinglab.core.data.home.HomeAssistantProblem
import app.pacinglab.core.data.home.HomeAssistantStatus
import app.pacinglab.core.designsystem.icon.PacingLabIcons
import app.pacinglab.core.model.home.HomeSensor
import app.pacinglab.feature.settings.R
import app.pacinglab.feature.settings.datasources.SectionCard
import app.pacinglab.feature.settings.datasources.StatusLine
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeAssistantRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: HomeAssistantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeAssistantScreen(
        state = state,
        onBack = onBack,
        modifier = modifier,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onLoadSensors = viewModel::loadSensors,
        onChoose = viewModel::choose,
        onSending = viewModel::setSending,
        onShowYaml = viewModel::showYaml,
        onHideYaml = viewModel::hideYaml,
    )
}

/**
 * The link to the athlete's Home Assistant: the address and token to connect, then the bedroom sensors
 * that feed Sleep factors, the switch that sends PacingLab's values to Home Assistant with the configuration
 * Home Assistant needs for them, and disconnecting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAssistantScreen(
    state: HomeAssistantUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onConnect: (String, String) -> Unit = { _, _ -> },
    onDisconnect: () -> Unit = {},
    onLoadSensors: () -> Unit = {},
    onChoose: (ClimateRole, HomeSensor?) -> Unit = { _, _ -> },
    onSending: (Boolean) -> Unit = {},
    onShowYaml: () -> Unit = {},
    onHideYaml: () -> Unit = {},
) {
    var picking by rememberSaveable { mutableStateOf<ClimateRole?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_assistant_title)) },
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
            item { ConnectionCard(state, zoneId, onConnect) }
            if (state.status.connected) {
                item {
                    BedroomCard(state.status) { role ->
                        picking = role
                        onLoadSensors()
                    }
                }
                item { SendCard(state.status, onSending, onShowYaml) }
                item { OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.home_assistant_disconnect)) } }
            }
        }
    }
    picking?.let { role ->
        SensorDialog(
            role = role,
            sensors = state.sensors,
            current = state.status.sensors[role],
            onChoose = {
                onChoose(role, it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
    state.yaml?.let { YamlDialog(it, onHideYaml) }
}

@Composable
private fun ConnectionCard(state: HomeAssistantUiState, zoneId: ZoneId, onConnect: (String, String) -> Unit) {
    val status = state.status
    SectionCard(title = stringResource(R.string.home_assistant_connection)) {
        if (status.connected) {
            ConnectedStatus(status, zoneId)
            return@SectionCard
        }
        Text(
            stringResource(R.string.home_assistant_connect_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var url by rememberSaveable { mutableStateOf("") }
        var token by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.home_assistant_url)) },
            placeholder = { Text(stringResource(R.string.home_assistant_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.home_assistant_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        when (val result = state.result) {
            ConnectResult.NotHttps -> Problem(stringResource(R.string.home_assistant_not_https))

            ConnectResult.TokenRejected -> Problem(stringResource(R.string.home_assistant_rejected))

            is ConnectResult.Unreachable -> Problem(
                result.reason?.let { stringResource(R.string.home_assistant_failed, it) }
                    ?: stringResource(R.string.home_assistant_failed_unknown),
            )

            else -> Unit
        }
        if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { onConnect(url, token) }, enabled = !state.connecting && url.isNotBlank() && token.isNotBlank()) {
            Text(stringResource(R.string.home_assistant_connect))
        }
    }
}

@Composable
private fun ConnectedStatus(status: HomeAssistantStatus, zoneId: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale, zoneId) { DateTimeFormatter.ofPattern("d MMM, HH:mm", locale).withZone(zoneId) }
    val server = status.serverName ?: status.url?.removePrefix("https://").orEmpty()
    StatusLine(PacingLabIcons.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.home_assistant_connected, server))
    when (status.problem) {
        HomeAssistantProblem.TOKEN_REJECTED -> Problem(stringResource(R.string.home_assistant_token_rejected))

        HomeAssistantProblem.TOKEN_LOST -> Problem(stringResource(R.string.home_assistant_token_lost))

        HomeAssistantProblem.UNREACHABLE -> StatusLine(
            PacingLabIcons.Schedule,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.home_assistant_unreachable),
        )

        null -> if (status.lastRead == null && status.lastSent == null) {
            StatusLine(PacingLabIcons.Sync, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.home_assistant_waiting))
        }
    }
    status.lastRead?.let {
        Detail(stringResource(R.string.home_assistant_last_read, time.format(it)))
    }
    status.lastSent?.let {
        Detail(stringResource(R.string.home_assistant_last_sent, time.format(it)))
    }
}

@Composable
private fun BedroomCard(status: HomeAssistantStatus, onPick: (ClimateRole) -> Unit) {
    SectionCard(title = stringResource(R.string.home_assistant_bedroom)) {
        Detail(stringResource(R.string.home_assistant_bedroom_help))
        Column {
            for ((i, role) in ClimateRole.entries.withIndex()) {
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { onPick(role) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(role.label()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        text = status.sensorNames[role] ?: stringResource(R.string.home_assistant_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (status.sensors.isNotEmpty()) Detail(pluralStringResource(R.plurals.home_assistant_nights, status.nights, status.nights))
    }
}

@Composable
private fun SendCard(status: HomeAssistantStatus, onSending: (Boolean) -> Unit, onShowYaml: () -> Unit) {
    SectionCard(title = stringResource(R.string.home_assistant_send)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Switch) { onSending(!status.sending) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_assistant_send_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = status.sending, onCheckedChange = null)
        }
        Detail(stringResource(R.string.home_assistant_send_summary))
        TextButton(onClick = onShowYaml) { Text(stringResource(R.string.home_assistant_show_yaml)) }
    }
}

@Composable
private fun SensorDialog(role: ClimateRole, sensors: SensorList, current: String?, onChoose: (HomeSensor?) -> Unit, onDismiss: () -> Unit) {
    val kind = stringResource(role.label()).lowercase(LocalConfiguration.current.locales[0])
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_assistant_cancel)) } },
        title = { Text(stringResource(R.string.home_assistant_choose, if (role == ClimateRole.CO2) "CO2" else kind)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (sensors) {
                    SensorList.Idle, SensorList.Loading -> {
                        Text(stringResource(R.string.home_assistant_loading), style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                    }

                    SensorList.Failed -> Text(
                        stringResource(R.string.home_assistant_sensors_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    is SensorList.Loaded -> {
                        val matching = sensors.sensors.filter { it.deviceClass == role.deviceClass }
                        Choice(stringResource(R.string.home_assistant_none), null, current == null) { onChoose(null) }
                        for (sensor in matching) {
                            Choice(sensor.name, listOfNotNull(sensor.state, sensor.unit).joinToString(" "), sensor.entityId == current) {
                                onChoose(sensor)
                            }
                        }
                        if (matching.isEmpty()) {
                            Text(
                                stringResource(R.string.home_assistant_no_sensors),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun Choice(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(
            min = 48.dp,
        ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun YamlDialog(yaml: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.home_assistant_yaml_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                // Sharing sends it to the laptop, where Home Assistant's file editor is easier to use.
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, yaml)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                ) { Text(stringResource(R.string.home_assistant_share)) }
                TextButton(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("pacinglab.yaml", yaml))) }
                }) {
                    Text(stringResource(R.string.home_assistant_copy))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_assistant_close)) } },
        title = { Text(stringResource(R.string.home_assistant_yaml_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_assistant_yaml_steps), style = MaterialTheme.typography.bodySmall)
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = yaml,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun Problem(text: String) {
    StatusLine(PacingLabIcons.Warning, MaterialTheme.colorScheme.tertiary, text)
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun ClimateRole.label(): Int = when (this) {
    ClimateRole.TEMPERATURE -> R.string.home_assistant_role_temperature
    ClimateRole.HUMIDITY -> R.string.home_assistant_role_humidity
    ClimateRole.CO2 -> R.string.home_assistant_role_co2
}
