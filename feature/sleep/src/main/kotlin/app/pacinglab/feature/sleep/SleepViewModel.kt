package app.pacinglab.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
class SleepViewModel @Inject constructor(nights: NightRepository, private val scheduler: SyncScheduler) : ViewModel() {
    val state: StateFlow<SleepUiState> = nights.observeSleep()
        .map { history -> SleepUiState.Loaded(history.nights, history.scores, history.needs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SleepUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.updating.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.syncNow()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
