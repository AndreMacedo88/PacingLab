package app.pacinglab.feature.settings.physiology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.zones.ThresholdEstimates
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.PhysiologyRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * The values in force today and the zones they give. [anchors] holds the resting heart rate in use: the
 * athlete's own when set, otherwise the one measured during sleep.
 */
data class CurrentPhysiology(val entry: PhysiologyEntry, val anchors: HeartRateAnchors, val fiveZones: ZoneSet, val threeZones: ZoneSet) {
    val thresholdEstimated: Boolean get() = entry.thresholdHr == null
    val restingSetByAthlete: Boolean get() = entry.restingHr != null
}

sealed interface PhysiologyUiState {
    data object Loading : PhysiologyUiState

    /**
     * @property entries newest first.
     * @property measuredRestingHr resting heart rate measured during sleep (median of the last 30 nights),
     *   used whenever an entry leaves resting heart rate empty; null before the watch has been worn at night.
     */
    data class Loaded(
        val entries: List<PhysiologyEntry>,
        val current: CurrentPhysiology?,
        val today: LocalDate,
        val measuredRestingHr: Int? = null,
    ) : PhysiologyUiState
}

@HiltViewModel
class PhysiologyViewModel @Inject constructor(
    private val repository: PhysiologyRepository,
    trainingLoad: TrainingLoadRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<PhysiologyUiState> = combine(repository.observeHistory(), trainingLoad.anchors) { history, timeline ->
        val today = LocalDate.now(clock)
        val measured = timeline.measuredRestingAt(today)
        PhysiologyUiState.Loaded(
            entries = history.entries.reversed(),
            current = history.at(today)?.let { current(it, measured) },
            today = today,
            measuredRestingHr = measured,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), PhysiologyUiState.Loading)

    fun save(entry: PhysiologyEntry, replacing: LocalDate?) {
        viewModelScope.launch { repository.save(entry, replacing) }
    }

    fun delete(effectiveFrom: LocalDate) {
        viewModelScope.launch { repository.delete(effectiveFrom) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun current(entry: PhysiologyEntry, measuredRestingHr: Int? = null): CurrentPhysiology {
    val own = ThresholdEstimates.anchors(entry)
    val anchors = if (own.restingHr == null && measuredRestingHr != null && measuredRestingHr < own.thresholdHr) {
        own.copy(restingHr = measuredRestingHr)
    } else {
        own
    }
    return CurrentPhysiology(
        entry = entry,
        anchors = anchors,
        fiveZones = Zones.of(ZoneModel.LTHR_FIVE, anchors),
        threeZones = Zones.of(ZoneModel.POLARIZED_THREE, anchors),
    )
}
