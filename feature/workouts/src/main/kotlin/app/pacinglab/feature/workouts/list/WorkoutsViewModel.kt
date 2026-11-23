package app.pacinglab.feature.workouts.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.CalendarDay
import app.pacinglab.core.analytics.load.TrainingCalendar
import app.pacinglab.core.data.repository.ExcludedWorkoutRepository
import app.pacinglab.core.data.repository.SessionKindRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.WorkoutListRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.HealthSync
import app.pacinglab.core.data.sync.SyncOutcome
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.data.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * @property sessions every session, newest first.
 * @property shown the sessions [filter] lets through, in [sort] order.
 * @property calendar every day with training, for the month grid.
 */
data class WorkoutsUiState(
    val loading: Boolean = true,
    val sessions: List<SessionItem> = emptyList(),
    val sync: SyncStatus = SyncStatus.Idle,
    val lastOutcome: SyncOutcome? = null,
    val refreshing: Boolean = false,
    val sort: WorkoutSort = WorkoutSort.NEWEST,
    val filter: WorkoutFilter = WorkoutFilter(),
    val calendar: Map<LocalDate, CalendarDay> = emptyMap(),
) {
    val shown: List<SessionItem> by lazy { sessions.arranged(filter, sort) }
}

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    repository: WorkoutRepository,
    kinds: SessionKindRepository,
    trainingLoad: TrainingLoadRepository,
    excludedWorkouts: ExcludedWorkoutRepository,
    healthSync: HealthSync,
    private val list: WorkoutListRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    private val view = list.view.map { view ->
        val sort = WorkoutSort.entries.firstOrNull { it.name == view.sort } ?: WorkoutSort.NEWEST
        sort to WorkoutFilter.of(view.filters)
    }

    /** The load of each workout, for the week headings, the calendar and the order by load. */
    private val loads = trainingLoad.observeWorkoutLoads()

    private val sessions = combine(
        repository.observeSessions(),
        kinds.observeKinds(),
        loads.map { all -> all.associate { it.workoutId to it.scores?.stressScore } },
        excludedWorkouts.observe(),
    ) { sessions, byId, loadById, excluded ->
        sessions.map { session ->
            session.toItem(byId[session.id]).copy(
                load = session.workouts.mapNotNull { loadById[it.id] }.takeIf { it.isNotEmpty() }?.sum(),
                leftOut = session.workouts.any { it.id in excluded },
            )
        }
    }

    /** Training by day, as the month grid shows it; a day counts sessions, not workouts. */
    private val calendar = combine(loads, kinds.observeKindsByWorkout(), repository.observeSessions()) { all, kindsByWorkout, sessions ->
        TrainingCalendar.days(all, kindsByWorkout, sessions.flatMap { s -> s.workouts.map { it.id to s.id } }.toMap())
    }.flowOn(Dispatchers.Default)

    private val sync = combine(healthSync.status, healthSync.lastOutcome, scheduler.refreshing, ::Triple)

    val state: StateFlow<WorkoutsUiState> = combine(
        sessions,
        view,
        sync,
        calendar.onStart { emit(emptyMap()) },
    ) { sessions, (sort, filter), (status, outcome, refreshing), days ->
        WorkoutsUiState(
            loading = false,
            sessions = sessions,
            sync = status,
            lastOutcome = outcome,
            refreshing = refreshing,
            sort = sort,
            filter = filter,
            calendar = days,
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
