package app.pacinglab.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.recovery.FeelingAgreement
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

sealed interface SleepUiState {
    data object Loading : SleepUiState

    /**
     * @property nights every night, oldest first.
     * @property scores the score of each night.
     * @property needs the sleep need going into each night.
     * @property rested how rested the athlete said they felt each morning, 1 to 5.
     */
    data class Loaded(
        val nights: List<Night>,
        val scores: Map<LocalDate, SleepScore>,
        val needs: Map<LocalDate, Long>,
        val rested: Map<LocalDate, Int> = emptyMap(),
    ) : SleepUiState {
        val feeling: FeelingAgreement by lazy { FeelingAgreement.of(scores, rested) }
    }
}

@HiltViewModel
class SleepViewModel @Inject constructor(nights: NightRepository, wellness: WellnessRepository, private val scheduler: SyncScheduler) :
    ViewModel() {
    val state: StateFlow<SleepUiState> = combine(nights.observeSleep(), wellness.observeRested()) { history, rested ->
        SleepUiState.Loaded(history.nights, history.scores, history.needs, rested)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SleepUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.updating.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.syncNow()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
