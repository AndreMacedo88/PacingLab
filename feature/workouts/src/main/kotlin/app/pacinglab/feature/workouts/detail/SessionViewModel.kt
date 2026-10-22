package app.pacinglab.feature.workouts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.intervals.RecordingMode
import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.session.SessionAnalysis
import app.pacinglab.core.analytics.session.SessionAnalyzer
import app.pacinglab.core.analytics.session.SessionClassifier
import app.pacinglab.core.analytics.session.SessionPart
import app.pacinglab.core.analytics.session.Split
import app.pacinglab.core.analytics.session.Splits
import app.pacinglab.core.analytics.workout.SpeedCleaning
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.EffortRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The whole session or one of its recorded parts, with everything the screen shows about it.
 *
 * @property title the part's name as the watch gave it; null for the whole session.
 * @property routes GPS tracks recorded in this scope, in time order.
 * @property routeOnlyFrom titles of the parts that carry the [routes] when other moving parts have none,
 *   empty otherwise.
 * @property recording how the part was recorded; null for the whole session of several parts.
 * @property heartRate heart rate over the scope; for the whole session the parts' series one after another.
 * @property splits kilometre splits of a steady run or walk recorded as one workout; empty otherwise.
 * @property kind what the run was for; null for other sports.
 * @property role [PhaseKind.WARM_UP] or [PhaseKind.COOL_DOWN] for a recorded part that only warmed up or
 *   cooled down around the main part; null otherwise.
 * @property workoutIds the recorded workouts the scope covers, which an effort rating applies to.
 */
data class DetailScope(
    val title: String?,
    val sport: Sport,
    val start: Instant,
    val end: Instant,
    val zoneOffset: ZoneOffset?,
    val movingMillis: Long,
    val distanceMeters: Double?,
    val averageHr: Int?,
    val maxHr: Int?,
    val routes: List<Route>,
    val routeOnlyFrom: List<String>,
    val analysis: SessionAnalysis,
    val recording: RecordingItem?,
    val heartRate: HeartRateSeries = HeartRateSeries.EMPTY,
    val splits: List<Split> = emptyList(),
    val kind: SessionKind? = null,
    val role: PhaseKind? = null,
    val workoutIds: List<String> = emptyList(),
)

/** Where a recorded part came from and how much data it carries. */
data class RecordingItem(
    val origin: String,
    val method: RecordingMethod,
    val activity: String,
    val heartRateSamples: Int,
    val speedSamples: Int,
    val routePoints: Int,
    val laps: Int,
) {
    /** Watches record no GPS in interval mode, which deserves a word when the map is missing. */
    val isIntervalMode: Boolean get() = RecordingMode.of(activity) == RecordingMode.INTERVALS
}

sealed interface SessionUiState {
    data object Loading : SessionUiState

    data object NotFound : SessionUiState

    /**
     * @property scopes the whole session first, then each recorded part when there are several. Time in
     *   zones in each scope's analysis lists the five-zone model, then the polarized model.
     * @property zonesAvailable false until the athlete has set a maximal heart rate.
     * @property efforts the athlete's effort ratings of the session's workouts, by workout id.
     * @property speedByDefault running speed shows as km/h rather than pace until toggled.
     */
    data class Loaded(
        val session: SessionItem,
        val scopes: List<DetailScope>,
        val zonesAvailable: Boolean,
        val efforts: Map<String, Int> = emptyMap(),
        val speedByDefault: Boolean = false,
    ) : SessionUiState {
        /** The rating of the workouts in [scope] when they share one; null when unrated. */
        fun effortOf(scope: DetailScope): Int? = scope.workoutIds.map { efforts[it] }.distinct().singleOrNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SessionViewModel.Factory::class)
class SessionViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val repository: WorkoutRepository,
    trainingLoad: TrainingLoadRepository,
    private val ratings: EffortRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {
    private val analysed = combine(repository.observeSessions(), trainingLoad.anchors) { sessions, timeline ->
        sessions.firstOrNull { it.id == sessionId } to timeline
    }
        .mapLatest { (session, timeline) -> if (session == null) SessionUiState.NotFound else load(session, timeline) }
        .flowOn(Dispatchers.Default)

    // Ratings and preferences join after the analysis so that rating a session does not analyse it again.
    val state: StateFlow<SessionUiState> = combine(
        analysed,
        ratings.observe(),
        preferences.preferences.map { it.speedByDefault }.distinctUntilChanged(),
    ) { state, rated, speed ->
        if (state is SessionUiState.Loaded) {
            val ids = state.scopes.first().workoutIds.toSet()
            state.copy(efforts = rated.filterKeys { it in ids }, speedByDefault = speed)
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SessionUiState.Loading)

    /** Rates the workouts of a scope, or clears their rating when [effort] is the one they already have. */
    fun rateEffort(workoutIds: List<String>, effort: Int) {
        viewModelScope.launch {
            val current = (state.value as? SessionUiState.Loaded)?.efforts
            if (current != null && workoutIds.all { current[it] == effort }) {
                workoutIds.forEach { ratings.clear(it) }
            } else {
                workoutIds.forEach { ratings.rate(it, effort) }
            }
        }
    }

    private suspend fun load(session: TrainingSession, timeline: AnchorTimeline): SessionUiState.Loaded {
        val parts = session.workouts.map { SessionPart(it, repository.streams(it.id)) }
        val anchors = timeline.at(TrainingLoads.dateOf(session.workouts.first(), ZoneId.systemDefault()))
        val zoneSets = anchors?.let { listOf(Zones.of(ZoneModel.LTHR_FIVE, it), Zones.of(ZoneModel.POLARIZED_THREE, it)) }.orEmpty()
        val five = zoneSets.firstOrNull()
        val analysis = SessionAnalyzer.analyze(parts, zoneSets, anchors)
        val joined = HeartRateSeries.joined(parts.map { it.streams.heartRate })
        val kind = if (session.sport == Sport.RUN) {
            SessionClassifier.classify(analysis, joined, five, parts.sumOf { it.workout.summary.durationMillis })
        } else {
            null
        }
        val item = session.toItem(kind)
        val whole = DetailScope(
            title = null,
            sport = item.sport,
            start = session.start,
            end = session.workouts.maxOf { it.end },
            zoneOffset = item.zoneOffset,
            movingMillis = item.movingMillis,
            distanceMeters = item.distanceMeters,
            averageHr = item.averageHr,
            maxHr = item.maxHr,
            routes = parts.map { it.streams.route }.filterNot { it.isEmpty() },
            routeOnlyFrom = routeOnlyFrom(parts),
            analysis = analysis,
            recording = parts.singleOrNull()?.recording(),
            heartRate = joined,
            kind = kind,
            workoutIds = session.workouts.map { it.id },
        ).let { scope -> parts.singleOrNull()?.let { scope.copy(splits = it.splits(scope.analysis)) } ?: scope }
        val scopes = if (parts.size == 1) listOf(whole) else listOf(whole) + parts.map { it.scope(zoneSets, anchors, it.roleIn(analysis)) }
        return SessionUiState.Loaded(session = item, scopes = scopes, zonesAvailable = zoneSets.isNotEmpty())
    }

    /** Warm-up or cool-down when the session's analysis took this whole part for one. */
    private fun SessionPart.roleIn(analysis: SessionAnalysis): PhaseKind? = analysis.phases.firstOrNull {
        it.startMillis == workout.start.toEpochMilli() && it.endMillis == workout.end.toEpochMilli()
    }?.kind?.takeIf { it == PhaseKind.WARM_UP || it == PhaseKind.COOL_DOWN }

    private fun SessionPart.scope(zoneSets: List<ZoneSet>, anchors: HeartRateAnchors?, role: PhaseKind?) = DetailScope(
        title = workout.title,
        sport = workout.sport,
        start = workout.start,
        end = workout.end,
        zoneOffset = workout.zoneOffset,
        movingMillis = workout.summary.durationMillis,
        distanceMeters = workout.summary.distanceMeters,
        averageHr = workout.summary.averageHr,
        maxHr = workout.summary.maxHr,
        routes = listOf(streams.route).filterNot { it.isEmpty() },
        routeOnlyFrom = emptyList(),
        analysis = SessionAnalyzer.analyze(listOf(this), zoneSets, anchors),
        recording = recording(),
        heartRate = streams.heartRate,
        role = role,
        workoutIds = listOf(workout.id),
    ).let { scope ->
        val kind = if (workout.sport == Sport.RUN && role == null) {
            SessionClassifier.classify(scope.analysis, streams.heartRate, zoneSets.firstOrNull(), workout.summary.durationMillis)
        } else {
            null
        }
        scope.copy(splits = splits(scope.analysis), kind = kind)
    }

    /** Splits of a steady run or walk; an interval session has its rep table instead. */
    private fun SessionPart.splits(analysis: SessionAnalysis): List<Split> {
        if (workout.sport !in setOf(Sport.RUN, Sport.WALK) || analysis.isIntervalSession) return emptyList()
        val speed = SpeedCleaning.dropImplausible(streams.speed, SpeedCleaning.maxPlausibleSpeed(workout.sport))
        return Splits.of(streams.route, speed, streams.heartRate, workout.summary.distanceMeters)
    }

    private fun SessionPart.recording() = RecordingItem(
        origin = workout.origin,
        method = workout.recordingMethod,
        activity = workout.activity,
        heartRateSamples = streams.heartRate.size,
        speedSamples = streams.speed.size,
        routePoints = streams.route.size,
        laps = workout.laps.size,
    )

    /** Parts with GPS, named, when some moving part of the session has none. */
    private fun routeOnlyFrom(parts: List<SessionPart>): List<String> {
        val (withRoute, withoutRoute) = parts.filter { it.workout.sport in MOVING }.partition { !it.streams.route.isEmpty() }
        return if (withRoute.isEmpty() || withoutRoute.isEmpty()) emptyList() else withRoute.mapNotNull { it.workout.title }
    }

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): SessionViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val MOVING = setOf(Sport.RUN, Sport.WALK, Sport.RIDE)
    }
}
