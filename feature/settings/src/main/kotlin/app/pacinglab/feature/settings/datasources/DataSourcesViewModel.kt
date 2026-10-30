package app.pacinglab.feature.settings.datasources

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.GoogleHealthRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.HealthSync
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.googlehealth.GoogleHealthAccess
import app.pacinglab.core.googlehealth.GoogleHealthAuthorization
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
    private val googleHealth: GoogleHealthRepository,
    private val googleHealthAuthorization: GoogleHealthAuthorization,
) : ViewModel() {
    private val _state = MutableStateFlow(DataSourcesUiState())
    val state: StateFlow<DataSourcesUiState> = _state.asStateFlow()

    private val _consent = MutableStateFlow<PendingIntent?>(null)

    /** Google's consent screen, to be started by the screen once and then cleared with [consentStarted]. */
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()

    val requestedPermissions: Set<String> = HealthConnectPermissions.all

    val permissionContract: ActivityResultContract<Set<String>, Set<String>>
        get() = healthConnect.permissionRequestContract()

    val healthConnectSettingsIntent: Intent get() = healthConnect.settingsIntent()

    val healthConnectFallbackIntent: Intent get() = healthConnect.fallbackSettingsIntent()

    val exportSessionCount: Int = RawSessionExporter.DEFAULT_SESSIONS

    init {
        refresh()
        viewModelScope.launch {
            combine(healthSync.status, healthSync.info, healthSync.lastOutcome, workouts.observeWorkoutCount(), SyncCardState::of)
                .collect { sync -> _state.update { it.copy(sync = sync) } }
        }
        viewModelScope.launch {
            googleHealth.status.collect { status -> _state.update { it.copy(googleHealth = it.googleHealth.copy(status = status)) } }
        }
    }

    /** Asks for access to the Google Health API; shows Google's consent screen when needed. */
    fun connectGoogleHealth() {
        updateGoogleHealth { it.copy(connecting = true, failure = null) }
        viewModelScope.launch { handle(googleHealthAuthorization.authorize()) }
    }

    fun consentStarted() {
        _consent.value = null
    }

    /** Called with the result of Google's consent screen. */
    fun onConsentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            updateGoogleHealth { it.copy(connecting = false) }
            return
        }
        viewModelScope.launch { handle(googleHealthAuthorization.fromConsent(data)) }
    }

    fun disconnectGoogleHealth() {
        viewModelScope.launch { googleHealth.disconnect() }
    }

    private suspend fun handle(access: GoogleHealthAccess) {
        when (access) {
            is GoogleHealthAccess.Granted -> {
                googleHealth.connect()
                updateGoogleHealth { it.copy(connecting = false) }
            }

            is GoogleHealthAccess.NeedsConsent -> _consent.value = access.intent

            is GoogleHealthAccess.Failed -> updateGoogleHealth { it.copy(connecting = false, failure = access.reason) }
        }
    }

    private fun updateGoogleHealth(change: (GoogleHealthCardState) -> GoogleHealthCardState) {
        _state.update { it.copy(googleHealth = change(it.googleHealth)) }
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
            val available = availability == HealthConnectAvailability.AVAILABLE
            val granted = if (available) healthConnect.grantedPermissions() else emptySet()
            val features = if (available) healthConnect.featureSupport() else null
            _state.update {
                it.withAccess(availability, requestedPermissions, granted, features, HealthConnectPermissions.READ_EXERCISE_ROUTES)
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
