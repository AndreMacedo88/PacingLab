package app.pacinglab.feature.settings.physiology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.zones.ThresholdEstimates
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.PhysiologyRepository
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The values in force today and the zones they give. */
data class CurrentPhysiology(val entry: PhysiologyEntry, val anchors: HeartRateAnchors, val fiveZones: ZoneSet, val threeZones: ZoneSet) {
    val thresholdEstimated: Boolean get() = entry.thresholdHr == null
}

sealed interface PhysiologyUiState {
    data object Loading : PhysiologyUiState

    /** [entries] newest first. */
    data class Loaded(val entries: List<PhysiologyEntry>, val current: CurrentPhysiology?, val today: LocalDate) : PhysiologyUiState
}

@HiltViewModel
class PhysiologyViewModel @Inject constructor(private val repository: PhysiologyRepository, private val clock: Clock) : ViewModel() {
    val state: StateFlow<PhysiologyUiState> = repository.observeHistory()
        .map { history ->
            val today = LocalDate.now(clock)
            PhysiologyUiState.Loaded(
                entries = history.entries.reversed(),
                current = history.at(today)?.let(::current),
                today = today,
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

internal fun current(entry: PhysiologyEntry): CurrentPhysiology {
    val anchors = ThresholdEstimates.anchors(entry)
    return CurrentPhysiology(
        entry = entry,
        anchors = anchors,
        fiveZones = Zones.of(ZoneModel.LTHR_FIVE, anchors),
        threeZones = Zones.of(ZoneModel.POLARIZED_THREE, anchors),
    )
}
