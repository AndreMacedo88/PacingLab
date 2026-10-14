package app.pacinglab.feature.settings.datasources

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.HealthSync
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.healthconnect.HealthConnectAvailability
import app.pacinglab.core.healthconnect.HealthConnectManager
import app.pacinglab.core.healthconnect.HealthConnectPermissions
import app.pacinglab.core.healthconnect.probe.HealthDataProbe
import app.pacinglab.core.healthconnect.probe.ProbeFileStore
import app.pacinglab.core.healthconnect.probe.RawSessionExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataSourcesViewModel @Inject constructor(
    private val healthConnect: HealthConnectManager,
    private val probe: HealthDataProbe,
    private val exporter: RawSessionExporter,
    private val files: ProbeFileStore,
    healthSync: HealthSync,
    workouts: WorkoutRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(DataSourcesUiState())
    val state: StateFlow<DataSourcesUiState> = _state.asStateFlow()

    val requestedPermissions: Set<String> = HealthConnectPermissions.all

    val permissionContract: ActivityResultContract<Set<String>, Set<String>>
        get() = healthConnect.permissionRequestContract()

    val healthConnectSettingsIntent: Intent get() = healthConnect.settingsIntent()

    val healthConnectFallbackIntent: Intent get() = healthConnect.fallbackSettingsIntent()

    val exportSessionCount: Int = RawSessionExporter.DEFAULT_SESSIONS

    init {
        refresh()
        viewModelScope.launch {
            combine(healthSync.status, healthSync.info, healthSync.lastOutcome, workouts.observeWorkoutCount()) {
                    status,
                    info,
                    outcome,
                    count,
                ->
                SyncCardState(
                    status = status,
                    lastSuccess = info.lastSuccess,
                    importComplete = info.importComplete,
                    workoutCount = count,
                    problem = outcome?.errors?.firstOrNull(),
                    paused = outcome?.rateLimited == true,
                )
            }.collect { sync -> _state.update { it.copy(sync = sync) } }
        }
    }

    fun syncNow() = scheduler.syncNow()

    /** Called with the permissions granted in Health Connect's dialog. */
    fun onPermissionsResult() {
        refresh()
        scheduler.syncNow()
    }

    fun refresh() {
        viewModelScope.launch {
            val availability = healthConnect.availability()
            if (availability != HealthConnectAvailability.AVAILABLE) {
                _state.update { it.copy(availability = availability) }
                return@launch
            }
            val granted = healthConnect.grantedPermissions()
            val missing = requestedPermissions - granted
            _state.update {
                it.copy(
                    availability = availability,
                    grantedCount = requestedPermissions.size - missing.size,
                    requestedCount = requestedPermissions.size,
                    onlyRoutesMissing = missing == setOf(HealthConnectPermissions.READ_EXERCISE_ROUTES),
                    features = healthConnect.featureSupport(),
                )
            }
        }
    }

    fun runProbe() {
        if (_state.value.probe is ProbeState.Running) return
        _state.update { it.copy(probe = ProbeState.Running(progress = null)) }
        viewModelScope.launch {
            val next = try {
                val report = probe.run { progress -> _state.update { it.copy(probe = ProbeState.Running(progress)) } }
                ProbeState.Finished(ProbeHighlights.from(report), files.save(report))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProbeState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
            _state.update { it.copy(probe = next) }
        }
    }

    fun exportRawSessions() {
        if (_state.value.export is ExportState.Running) return
        _state.update { it.copy(export = ExportState.Running) }
        viewModelScope.launch {
            val next = try {
                val export = exporter.export(exportSessionCount)
                ExportState.Finished(export.sessions.size, files.save(export))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportState.Failed(e.message ?: e::class.simpleName.orEmpty())
            }
            _state.update { it.copy(export = next) }
        }
    }
}
