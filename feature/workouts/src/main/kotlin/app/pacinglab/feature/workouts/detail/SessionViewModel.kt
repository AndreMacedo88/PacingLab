package app.pacinglab.feature.workouts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.session.SessionAnalysis
import app.pacinglab.core.analytics.session.SessionAnalyzer
import app.pacinglab.core.analytics.session.SessionPart
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.analytics.zones.ThresholdEstimates
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.PhysiologyRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.model.PhysiologyHistory
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.feature.workouts.list.SessionItem
import app.pacinglab.feature.workouts.list.toItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** One recorded workout within a session, with how much data it carries. */
data class PartItem(
    val id: String,
    val title: String?,
    val activity: String,
    val sport: Sport,
    val start: Instant,
    val end: Instant,
    val zoneOffset: ZoneOffset?,
    val heartRateSamples: Int,
    val speedSamples: Int,
    val routePoints: Int,
    val laps: Int,
)

sealed interface SessionUiState {
    data object Loading : SessionUiState

    data object NotFound : SessionUiState

    /**
     * @property analysis reps, recoveries and, when [zonesAvailable], time in zones in the order
     *   five-zone model, then polarized model.
     * @property zonesAvailable false until the athlete has set a maximal heart rate.
     */
    data class Loaded(val session: SessionItem, val parts: List<PartItem>, val analysis: SessionAnalysis, val zonesAvailable: Boolean) :
        SessionUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SessionViewModel.Factory::class)
class SessionViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val repository: WorkoutRepository,
    physiology: PhysiologyRepository,
) : ViewModel() {
    val state: StateFlow<SessionUiState> = combine(repository.observeSessions(), physiology.observeHistory()) { sessions, history ->
        sessions.firstOrNull { it.id == sessionId } to history
    }
        .mapLatest { (session, history) -> if (session == null) SessionUiState.NotFound else load(session, history) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState.Loading)

    private suspend fun load(session: TrainingSession, history: PhysiologyHistory): SessionUiState.Loaded {
        val streams = session.workouts.associate { it.id to repository.streams(it.id) }
        val date = session.start.atZone(session.workouts.first().zoneOffset ?: ZoneId.systemDefault()).toLocalDate()
        val zoneSets = history.at(date)?.let(ThresholdEstimates::anchors)?.let { anchors ->
            listOf(Zones.of(ZoneModel.LTHR_FIVE, anchors), Zones.of(ZoneModel.POLARIZED_THREE, anchors))
        }.orEmpty()
        val analysis = SessionAnalyzer.analyze(session.workouts.map { SessionPart(it, streams.getValue(it.id)) }, zoneSets)
        return SessionUiState.Loaded(
            session = session.toItem(),
            parts = session.workouts.map { it.toPart(streams.getValue(it.id)) },
            analysis = analysis,
            zonesAvailable = zoneSets.isNotEmpty(),
        )
    }

    private fun Workout.toPart(streams: WorkoutStreams) = PartItem(
        id = id,
        title = title,
        activity = activity,
        sport = sport,
        start = start,
        end = end,
        zoneOffset = zoneOffset,
        heartRateSamples = streams.heartRate.size,
        speedSamples = streams.speed.size,
        routePoints = streams.route.size,
        laps = laps.size,
    )

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): SessionViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
