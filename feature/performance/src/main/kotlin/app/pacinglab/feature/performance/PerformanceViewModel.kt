package app.pacinglab.feature.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.performance.FitnessAge
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.EfficiencyHistory
import app.pacinglab.core.data.repository.PerformanceRepository
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.RunningPerformance
import app.pacinglab.core.data.repository.SessionKindRepository
import app.pacinglab.core.data.repository.SessionRecoveryPoint
import app.pacinglab.core.data.repository.Sex
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.Vo2MaxHistory
import app.pacinglab.core.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface PerformanceUiState {
    data object Loading : PerformanceUiState

    data class Loaded(
        val today: LocalDate,
        val vo2max: Vo2MaxHistory,
        val efficiency: EfficiencyHistory,
        val running: RunningPerformance,
        val cards: List<PlacedCard> = DashboardLayout().arrange(PerformanceCardKind.catalog),
        val fitness: FitnessComparison? = null,
        val recoveries: List<SessionRecoveryPoint> = emptyList(),
    ) : PerformanceUiState
}

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val performance: PerformanceRepository,
    clock: Clock,
    private val scheduler: SyncScheduler,
    private val dashboards: DashboardRepository,
    preferences: UserPreferencesRepository,
    sessions: SessionKindRepository,
) : ViewModel() {
    private val extras = combine(preferences.preferences, sessions.observeRecoveries()) { prefs, recoveries -> prefs to recoveries }

    val state: StateFlow<PerformanceUiState> = combine(
        performance.observeVo2Max(),
        performance.observeEfficiency(),
        performance.observeRunning(),
        dashboards.layout(Dashboard.PERFORMANCE),
        extras,
    ) { vo2max, efficiency, running, layout, (prefs, recoveries) ->
        val today = LocalDate.now(clock)
        val female = prefs.sex == Sex.FEMALE
        val age = prefs.ageOn(today)
        PerformanceUiState.Loaded(
            today = today,
            vo2max = vo2max,
            efficiency = efficiency,
            running = running,
            cards = layout.arrange(PerformanceCardKind.catalog),
            fitness = vo2max.trend?.current?.value?.let { value ->
                FitnessComparison(FitnessAge.of(value, female), age, age?.let { FitnessAge.average(it.toDouble(), female) })
            },
            recoveries = recoveries,
        )
    }.stateIn<PerformanceUiState>(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PerformanceUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.refreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.refresh()

    fun arrange(cards: List<PlacedCard>) {
        viewModelScope.launch { dashboards.save(Dashboard.PERFORMANCE, cards) }
    }

    fun resetArrangement() {
        viewModelScope.launch { dashboards.reset(Dashboard.PERFORMANCE) }
    }

    fun saveTest(date: LocalDate, value: Double) {
        viewModelScope.launch { performance.saveTest(date, value) }
    }

    fun deleteTest(date: LocalDate) {
        viewModelScope.launch { performance.deleteTest(date) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
