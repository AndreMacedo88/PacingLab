package app.pacinglab.feature.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.data.repository.TrainingLoadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
     */
    data class Loaded(val days: List<FormDay>, val weeks: List<WeekSummary>, val today: LocalDate) : TrendsUiState
}

@HiltViewModel
class TrendsViewModel @Inject constructor(repository: TrainingLoadRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<TrendsUiState> = repository.observeWorkoutLoads()
        .map { loads ->
            val today = LocalDate.now(clock)
            val days = FitnessModel.series(TrainingLoads.daily(loads), until = today)
            if (days.isEmpty() && loads.isNotEmpty()) {
                TrendsUiState.NeedsHeartRateValues
            } else {
                TrendsUiState.Loaded(days, TrainingLoads.weeks(loads, until = today, count = WEEKS), today)
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrendsUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val WEEKS = 8
    }
}
