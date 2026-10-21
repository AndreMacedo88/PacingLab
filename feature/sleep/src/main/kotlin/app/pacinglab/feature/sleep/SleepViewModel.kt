package app.pacinglab.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.data.repository.NightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

sealed interface SleepUiState {
    data object Loading : SleepUiState

    /**
     * @property nights every night, oldest first.
     * @property scores the score of each night.
     * @property needs the sleep need going into each night.
     */
    data class Loaded(val nights: List<Night>, val scores: Map<LocalDate, SleepScore>, val needs: Map<LocalDate, Long>) : SleepUiState
}

@HiltViewModel
class SleepViewModel @Inject constructor(nights: NightRepository) : ViewModel() {
    val state: StateFlow<SleepUiState> = nights.observeNights()
        .map { all -> SleepUiState.Loaded(all, SleepScore.all(all), all.associate { it.date to SleepScore.need(all, it.date) }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SleepUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
