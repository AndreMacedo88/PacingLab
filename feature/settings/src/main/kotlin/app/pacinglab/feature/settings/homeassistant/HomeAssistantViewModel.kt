package app.pacinglab.feature.settings.homeassistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.home.ClimateRole
import app.pacinglab.core.data.home.ConnectResult
import app.pacinglab.core.data.home.HomeAssistantRepository
import app.pacinglab.core.data.home.HomeAssistantStatus
import app.pacinglab.core.model.home.HomeSensor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The server's sensors, as far as they have been read for choosing a bedroom sensor. */
sealed interface SensorList {
    data object Idle : SensorList

    data object Loading : SensorList

    data class Loaded(val sensors: List<HomeSensor>) : SensorList

    data object Failed : SensorList
}

/**
 * @property connecting a connection is being checked; [result] is the last check's outcome when it failed.
 * @property yaml the configuration to show, while it is shown.
 */
data class HomeAssistantUiState(
    val status: HomeAssistantStatus = HomeAssistantStatus(),
    val connecting: Boolean = false,
    val result: ConnectResult? = null,
    val sensors: SensorList = SensorList.Idle,
    val yaml: String? = null,
)

@HiltViewModel
class HomeAssistantViewModel @Inject constructor(private val repository: HomeAssistantRepository) : ViewModel() {
    private val local = MutableStateFlow(HomeAssistantUiState())

    val state: StateFlow<HomeAssistantUiState> = combine(repository.status, local) { status, ui -> ui.copy(status = status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeAssistantUiState())

    fun connect(url: String, token: String) {
        viewModelScope.launch {
            local.update { it.copy(connecting = true, result = null) }
            val result = repository.connect(url, token)
            local.update { it.copy(connecting = false, result = result.takeUnless { r -> r == ConnectResult.Connected }) }
            if (result == ConnectResult.Connected) repository.sync()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            local.value = HomeAssistantUiState()
        }
    }

    /** Reads the server's sensors for the chooser. */
    fun loadSensors() {
        viewModelScope.launch {
            local.update { it.copy(sensors = SensorList.Loading) }
            val sensors = try {
                SensorList.Loaded(repository.sensors())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SensorList.Failed
            }
            local.update { it.copy(sensors = sensors) }
        }
    }

    fun choose(role: ClimateRole, sensor: HomeSensor?) {
        viewModelScope.launch {
            repository.setSensor(role, sensor)
            repository.sync()
        }
    }

    fun setSending(sending: Boolean) {
        viewModelScope.launch {
            repository.setSending(sending)
            if (sending) repository.sync()
        }
    }

    fun showYaml() {
        viewModelScope.launch { local.update { it.copy(yaml = repository.yaml()) } }
    }

    fun hideYaml() = local.update { it.copy(yaml = null) }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
