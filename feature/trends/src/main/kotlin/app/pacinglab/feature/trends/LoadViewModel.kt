package app.pacinglab.feature.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.IntensityFocus
import app.pacinglab.core.analytics.load.LoadBand
import app.pacinglab.core.analytics.load.RunningWeek
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.analytics.load.WeeklyTargets
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface LoadUiState {
    data object Loading : LoadUiState

    /** There are workouts, but none can be scored: maximal or resting heart rate is missing. */
    data object NeedsHeartRateValues : LoadUiState

    /**
     * @property days the fitness-fatigue model from the first day with load to [today]; empty without workouts.
     * @property weeks the latest weeks, newest first.
     * @property status today's training status; null with too little history.
     * @property focus the intensity focus of the last four weeks; null with too little endurance training.
     * @property loadBands the load that builds fitness in each of [weeks], by Monday.
     * @property running running distance of the latest weeks against the athlete's tolerance, newest first.
     * @property fiveZones the weekly table splits time over the five zones rather than the three.
     */
    data class Loaded(
        val days: List<FormDay>,
        val weeks: List<WeekSummary>,
        val today: LocalDate,
        val cards: List<PlacedCard> = DashboardLayout().arrange(LoadCardKind.catalog),
        val status: TrainingStatus? = null,
        val focus: IntensityFocus? = null,
        val loadBands: Map<LocalDate, LoadBand> = emptyMap(),
        val running: List<RunningWeek> = emptyList(),
        val fiveZones: Boolean = true,
    ) : LoadUiState
}

/** Training > Load: what the training adds up to, from today's status to the weeks behind it. */
@HiltViewModel
class LoadViewModel @Inject constructor(
    repository: TrainingLoadRepository,
    clock: Clock,
    private val scheduler: SyncScheduler,
    private val dashboards: DashboardRepository,
    training: TrainingStatusRepository,
    private val preferences: UserPreferencesRepository,
    workouts: WorkoutRepository,
) : ViewModel() {
    private val standing = combine(training.observeStatus(), training.observeFocus()) { status, focus -> status to focus }

    /** The training session of every workout, so that the weekly table counts sessions. */
    private val sessionOf = workouts.observeSessions().map { sessions ->
        sessions.flatMap { session -> session.workouts.map { it.id to session.id } }.toMap()
    }

    private val content = combine(
        repository.observeWorkoutLoads(),
        dashboards.layout(Dashboard.LOAD),
        standing,
        preferences.preferences.map { it.runningIncreasePercent }.distinctUntilChanged(),
        sessionOf,
    ) { loads, layout, (status, focus), increase, sessions ->
        val today = LocalDate.now(clock)
        val days = FitnessModel.series(TrainingLoads.daily(loads), until = today)
        if (days.isEmpty() && loads.isNotEmpty()) {
            LoadUiState.NeedsHeartRateValues
        } else {
            val weeks = TrainingLoads.weeks(loads, until = today, count = WEEKS, sessionOf = sessions)
            LoadUiState.Loaded(
                days,
                weeks,
                today,
                layout.arrange(LoadCardKind.catalog),
                status = status,
                focus = focus,
                loadBands = WeeklyTargets.loadBands(days, weeks.map { it.start }),
                running = WeeklyTargets.runningWeeks(
                    loads.filter { it.sport == Sport.RUN }.mapNotNull { load -> load.distanceMeters?.let { load.date to it } },
                    until = today,
                    count = RUNNING_WEEKS,
                    tolerance = 1 + increase / 100.0,
                ),
            )
        }
    }
        .flowOn(Dispatchers.Default)

    // Kept apart, so that switching the weekly table's zones does not recompute everything.
    val state: StateFlow<LoadUiState> = combine(
        content,
        preferences.preferences.map { it.weeksInFiveZones }.distinctUntilChanged(),
    ) { state, five ->
        if (state is LoadUiState.Loaded) state.copy(fiveZones = five) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LoadUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.refreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.refresh()

    fun setWeeksInFiveZones(five: Boolean) {
        viewModelScope.launch { preferences.setWeeksInFiveZones(five) }
    }

    fun arrange(cards: List<PlacedCard>) {
        viewModelScope.launch { dashboards.save(Dashboard.LOAD, cards) }
    }

    fun resetArrangement() {
        viewModelScope.launch { dashboards.reset(Dashboard.LOAD) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val WEEKS = 8
        const val RUNNING_WEEKS = 12
    }
}
