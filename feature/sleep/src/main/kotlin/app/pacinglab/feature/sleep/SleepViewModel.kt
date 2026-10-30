package app.pacinglab.feature.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.recovery.FeelingAgreement
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.SleepDebt
import app.pacinglab.core.analytics.recovery.SleepHealth
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardLayout
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PlacedCard
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

sealed interface SleepUiState {
    data object Loading : SleepUiState

    /**
     * @property nights every night, oldest first.
     * @property scores the score of each night.
     * @property needs the sleep need going into each night.
     * @property health sleep health over the two weeks up to each morning that has enough nights.
     * @property debts sleep debt going into each morning that has enough nights.
     * @property rested how rested the athlete said they felt each morning, 1 to 5.
     * @property cards the athlete's arrangement of the screen, hidden cards included.
     * @property today the date now, for asking about this morning rather than a past one.
     */
    data class Loaded(
        val nights: List<Night>,
        val scores: Map<LocalDate, SleepScore>,
        val needs: Map<LocalDate, Long>,
        val rested: Map<LocalDate, Int> = emptyMap(),
        val health: Map<LocalDate, SleepHealth> = emptyMap(),
        val debts: Map<LocalDate, SleepDebt> = emptyMap(),
        val cards: List<PlacedCard> = DashboardLayout().arrange(SleepCardKind.catalog),
        val today: LocalDate? = null,
        val freeDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
    ) : SleepUiState {
        val feeling: FeelingAgreement by lazy { FeelingAgreement.of(scores, rested) }
    }
}

@HiltViewModel
class SleepViewModel @Inject constructor(
    nights: NightRepository,
    private val wellness: WellnessRepository,
    private val scheduler: SyncScheduler,
    private val dashboards: DashboardRepository,
    private val clock: Clock,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<SleepUiState> = combine(
        nights.observeSleep(),
        wellness.observeRested(),
        dashboards.layout(Dashboard.SLEEP),
        preferences.preferences.map { it.freeDays }.distinctUntilChanged(),
    ) { history, rested, layout, freeDays ->
        SleepUiState.Loaded(
            history.nights,
            history.scores,
            history.needs,
            rested,
            history.health,
            history.debts,
            layout.arrange(SleepCardKind.catalog),
            today = LocalDate.now(clock),
            freeDays = freeDays,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SleepUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.refreshing.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.refresh()

    /** Records how rested the athlete felt on the morning of [date], or forgets the answer when they pick it again. */
    fun rate(date: LocalDate, rating: Int) {
        viewModelScope.launch {
            val current = (state.value as? SleepUiState.Loaded)?.rested?.get(date)
            if (current == rating) wellness.clearRested(date) else wellness.setRested(date, rating)
        }
    }

    fun arrange(cards: List<PlacedCard>) {
        viewModelScope.launch { dashboards.save(Dashboard.SLEEP, cards) }
    }

    fun resetArrangement() {
        viewModelScope.launch { dashboards.reset(Dashboard.SLEEP) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
