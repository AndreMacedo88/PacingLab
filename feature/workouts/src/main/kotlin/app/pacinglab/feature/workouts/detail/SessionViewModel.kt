package app.pacinglab.feature.workouts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.feature.workouts.list.SessionItem
import app.pacinglab.feature.workouts.list.toItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
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

    data class Loaded(val session: SessionItem, val parts: List<PartItem>) : SessionUiState
}

@HiltViewModel(assistedFactory = SessionViewModel.Factory::class)
class SessionViewModel @AssistedInject constructor(@Assisted private val sessionId: String, private val repository: WorkoutRepository) :
    ViewModel() {
    val state: StateFlow<SessionUiState> = repository.observeSessions()
        .map { sessions ->
            val session = sessions.firstOrNull { it.id == sessionId } ?: return@map SessionUiState.NotFound
            SessionUiState.Loaded(session.toItem(), session.workouts.map { it.toPart() })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState.Loading)

    private suspend fun Workout.toPart(): PartItem {
        val streams = repository.streams(id)
        return PartItem(
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
    }

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): SessionViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
