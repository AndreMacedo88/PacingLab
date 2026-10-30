package app.pacinglab.feature.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.CalendarDay
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.IntensityFocus
import app.pacinglab.core.analytics.load.LoadBand
import app.pacinglab.core.analytics.load.RunningWeek
import app.pacinglab.core.analytics.load.TrainingCalendar
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.analytics.load.WeeklyTargets
import app.pacinglab.core.analytics.recovery.HrvVariation
import app.pacinglab.core.analytics.recovery.RecoveryDay
import app.pacinglab.core.analytics.recovery.RecoveryTrend
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface TrendsUiState {
    data object Loading : TrendsUiState

    /** There are workouts, but none can be scored: maximal or resting heart rate is missing. */
    data object NeedsHeartRateValues : TrendsUiState

    /**
     * @property days the fitness-fatigue model from the first day with load to [today]; empty without workouts.
     * @property weeks the latest weeks, newest first.
     * @property status today's training status; null with too little history.
     * @property focus the intensity focus of the last four weeks; null with too little endurance training.
     * @property hrvVariation the week's HRV coefficient of variation; null with too few nights.
     * @property loadBands the load that builds fitness in each of [weeks], by Monday.
     * @property running running distance of the latest weeks against the athlete's tolerance, newest first.
     * @property calendar every day with training, for the month grid.
     */
    data class Loaded(
        val days: List<FormDay>,
        val weeks: List<WeekSummary>,
        val today: LocalDate,
        val recovery: List<RecoveryDay> = emptyList(),
        val cards: List<PlacedCard> = DashboardLayout().arrange(TrendsCardKind.catalog),
        val status: TrainingStatus? = null,
        val focus: IntensityFocus? = null,
        val hrvVariation: HrvVariation? = null,
        val loadBands: Map<LocalDate, LoadBand> = emptyMap(),
        val running: List<RunningWeek> = emptyList(),
        val calendar: Map<LocalDate, CalendarDay> = emptyMap(),
    ) : TrendsUiState
}

@HiltViewModel
class TrendsViewModel @Inject constructor(
    repository: TrainingLoadRepository,
    nights: NightRepository,
    clock: Clock,
    private val scheduler: SyncScheduler,
    private val dashboards: DashboardRepository,
    training: TrainingStatusRepository,
) : ViewModel() {
    private val recovery = nights.observeSleep().map { sleep ->
        val today = LocalDate.now(clock)
        RecoveryTrend.of(sleep.nights, today, sleep.scores) to HrvVariation.of(sleep.nights, today)
    }

    private val standing = combine(training.observeStatus(), training.observeFocus()) { status, focus -> status to focus }

    val state: StateFlow<TrendsUiState> = combine(
        repository.observeWorkoutLoads(),
        recovery,
        dashboards.layout(Dashboard.TRENDS),
        standing,
    ) { loads, (recoveryDays, variation), layout, (status, focus) ->
        val today = LocalDate.now(clock)
        val days = FitnessModel.series(TrainingLoads.daily(loads), until = today)
        if (days.isEmpty() && loads.isNotEmpty()) {
            TrendsUiState.NeedsHeartRateValues
        } else {
            val weeks = TrainingLoads.weeks(loads, until = today, count = WEEKS)
            TrendsUiState.Loaded(
                days,
                weeks,
                today,
                recoveryDays,
                layout.arrange(TrendsCardKind.catalog),
                status = status,
                focus = focus,
                hrvVariation = variation,
                loadBands = WeeklyTargets.loadBands(days, weeks.map { it.start }),
                running = WeeklyTargets.runningWeeks(
                    loads.filter { it.sport == Sport.RUN }.mapNotNull { load -> load.distanceMeters?.let { load.date to it } },
                    until = today,
                    count = RUNNING_WEEKS,
                ),
                calendar = TrainingCalendar.days(loads),
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrendsUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.refreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.refresh()

    fun arrange(cards: List<PlacedCard>) {
        viewModelScope.launch { dashboards.save(Dashboard.TRENDS, cards) }
    }

    fun resetArrangement() {
        viewModelScope.launch { dashboards.reset(Dashboard.TRENDS) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val WEEKS = 8
        const val RUNNING_WEEKS = 12
    }
}
