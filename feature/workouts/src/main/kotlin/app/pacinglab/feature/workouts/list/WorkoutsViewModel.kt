package app.pacinglab.feature.workouts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.SessionKindRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.WorkoutListRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.HealthSync
import app.pacinglab.core.data.sync.SyncOutcome
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * @property sessions every session, newest first.
 * @property shown the sessions [filter] lets through, in [sort] order.
 */
data class WorkoutsUiState(
    val loading: Boolean = true,
    val sessions: List<SessionItem> = emptyList(),
    val sync: SyncStatus = SyncStatus.Idle,
    val lastOutcome: SyncOutcome? = null,
    val refreshing: Boolean = false,
    val sort: WorkoutSort = WorkoutSort.NEWEST,
    val filter: WorkoutFilter = WorkoutFilter(),
) {
    val shown: List<SessionItem> by lazy { sessions.arranged(filter, sort) }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    repository: WorkoutRepository,
    kinds: SessionKindRepository,
    trainingLoad: TrainingLoadRepository,
    healthSync: HealthSync,
    private val list: WorkoutListRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val view = list.view.map { view ->
        val sort = WorkoutSort.entries.firstOrNull { it.name == view.sort } ?: WorkoutSort.NEWEST
        sort to WorkoutFilter.of(view.filters)
    }

    /** Loads are worked out only while the list is ordered by them. */
    private val loads = view.map { (sort) -> sort == WorkoutSort.HIGHEST_LOAD }
        .distinctUntilChanged()
        .flatMapLatest { needed ->
            if (needed) {
                trainingLoad.observeWorkoutLoads().map { all ->
                    all.associate { it.workoutId to it.scores?.stressScore }
                }
            } else {
                flowOf(null)
            }
        }

    private val sessions = combine(repository.observeSessions(), kinds.observeKinds(), loads) { sessions, byId, loadById ->
        sessions.map { session ->
            session.toItem(byId[session.id]).copy(
                load = loadById?.let { loads -> session.workouts.mapNotNull { loads[it.id] }.takeIf { it.isNotEmpty() }?.sum() },
            )
        }
    }

    val state: StateFlow<WorkoutsUiState> = combine(
        sessions,
        view,
        healthSync.status,
        healthSync.lastOutcome,
        scheduler.refreshing,
    ) { sessions, (sort, filter), status, outcome, refreshing ->
        WorkoutsUiState(
            loading = false,
            sessions = sessions,
            sync = status,
            lastOutcome = outcome,
            refreshing = refreshing,
            sort = sort,
            filter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WorkoutsUiState())

    fun refresh() = scheduler.refresh()

    fun setSort(sort: WorkoutSort) {
        viewModelScope.launch { list.setSort(sort.name) }
    }

    fun setFilter(filter: WorkoutFilter) {
        viewModelScope.launch { list.setFilters(filter.names()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
