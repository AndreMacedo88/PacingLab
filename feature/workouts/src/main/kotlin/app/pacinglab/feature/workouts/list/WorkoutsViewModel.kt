package app.pacinglab.feature.workouts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.HealthSync
import app.pacinglab.core.data.sync.SyncOutcome
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WorkoutsUiState(
    val loading: Boolean = true,
    val sessions: List<SessionItem> = emptyList(),
    val sync: SyncStatus = SyncStatus.Idle,
    val lastOutcome: SyncOutcome? = null,
) {
    val refreshing: Boolean get() = sync != SyncStatus.Idle
}

@HiltViewModel
class WorkoutsViewModel @Inject constructor(repository: WorkoutRepository, healthSync: HealthSync, private val scheduler: SyncScheduler) :
    ViewModel() {
    val state: StateFlow<WorkoutsUiState> = combine(
        repository.observeSessions().map { sessions -> sessions.map { it.toItem() } },
        healthSync.status,
        healthSync.lastOutcome,
    ) { sessions, status, outcome ->
        WorkoutsUiState(loading = false, sessions = sessions, sync = status, lastOutcome = outcome)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutsUiState())

    fun refresh() = scheduler.syncNow()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
