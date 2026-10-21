package app.pacinglab.feature.settings.physiology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.zones.MaxHeartRateEstimate
import app.pacinglab.core.analytics.zones.ThresholdHeartRateEstimate
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.PhysiologyRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.PhysiologyHistory
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
 * The values in force today and the zones they give. [anchors] holds what is in use: the athlete's own
 * values from [entry] where set, PacingLab's estimates and measurements everywhere else.
 */
data class CurrentPhysiology(val entry: PhysiologyEntry?, val anchors: HeartRateAnchors, val fiveZones: ZoneSet, val threeZones: ZoneSet) {
    val maxEstimated: Boolean get() = entry?.maxHr == null
    val thresholdEstimated: Boolean get() = entry?.thresholdHr != anchors.thresholdHr
    val restingSetByAthlete: Boolean get() = entry?.restingHr != null
}

sealed interface PhysiologyUiState {
    data object Loading : PhysiologyUiState

    /**
     * @property entries newest first.
     * @property current null while there is neither an entry nor an estimate of maximal heart rate.
     * @property measuredRestingHr resting heart rate measured during sleep (median of the last 30 nights),
     *   used whenever an entry leaves resting heart rate empty; null before the watch has been worn at night.
     * @property estimatedMax maximal heart rate from the workouts, used whenever an entry leaves it empty.
     * @property estimatedThreshold threshold heart rate from the hardest half hour of running, used whenever
     *   an entry leaves it empty; without one, 90 % of the maximum stands in.
     */
    data class Loaded(
        val entries: List<PhysiologyEntry>,
        val current: CurrentPhysiology?,
        val today: LocalDate,
        val measuredRestingHr: Int? = null,
        val estimatedMax: MaxHeartRateEstimate? = null,
        val estimatedThreshold: ThresholdHeartRateEstimate? = null,
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
        PhysiologyUiState.Loaded(
            entries = history.entries.reversed(),
            current = current(history.at(today), timeline.at(today)),
            today = today,
            measuredRestingHr = timeline.measuredRestingAt(today),
            estimatedMax = timeline.estimatedMax,
            estimatedThreshold = timeline.estimatedThresholdAt(today),
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

internal fun current(entry: PhysiologyEntry?, anchors: HeartRateAnchors?): CurrentPhysiology? = anchors?.let {
    CurrentPhysiology(
        entry = entry,
        anchors = it,
        fiveZones = Zones.of(ZoneModel.LTHR_FIVE, it),
        threeZones = Zones.of(ZoneModel.POLARIZED_THREE, it),
    )
}

/** What [entry] means today given PacingLab's own values; for previews and tests. */
internal fun current(
    entry: PhysiologyEntry?,
    estimatedMax: MaxHeartRateEstimate? = null,
    measuredRestingHr: Int? = null,
    today: LocalDate = entry?.effectiveFrom ?: LocalDate.MIN,
): CurrentPhysiology? {
    val timeline = AnchorTimeline(
        history = PhysiologyHistory(listOfNotNull(entry)),
        measuredResting = measuredRestingHr?.let { mapOf(today to it.toDouble()) }.orEmpty(),
        estimatedMax = estimatedMax,
    )
    return current(entry, timeline.at(today))
}
