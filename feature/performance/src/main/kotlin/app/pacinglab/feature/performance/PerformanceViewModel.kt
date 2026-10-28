package app.pacinglab.feature.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.EfficiencyHistory
import app.pacinglab.core.data.repository.PerformanceRepository
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.RunningPerformance
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
    ) : PerformanceUiState
}

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val performance: PerformanceRepository,
    clock: Clock,
    private val scheduler: SyncScheduler,
    private val dashboards: DashboardRepository,
) : ViewModel() {
    val state: StateFlow<PerformanceUiState> = combine(
        performance.observeVo2Max(),
        performance.observeEfficiency(),
        performance.observeRunning(),
        dashboards.layout(Dashboard.PERFORMANCE),
    ) { vo2max, efficiency, running, layout ->
        PerformanceUiState.Loaded(LocalDate.now(clock), vo2max, efficiency, running, layout.arrange(PerformanceCardKind.catalog))
    }.stateIn<PerformanceUiState>(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PerformanceUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.updating.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.syncNow()

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
