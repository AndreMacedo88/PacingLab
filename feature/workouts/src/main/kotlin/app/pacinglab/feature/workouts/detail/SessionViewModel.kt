package app.pacinglab.feature.workouts.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.geo.speeds
import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.analytics.intervals.RecordingMode
import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.BanisterWeighting
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.performance.AerobicDecoupling
import app.pacinglab.core.analytics.performance.Decoupling
import app.pacinglab.core.analytics.session.FormMinute
import app.pacinglab.core.analytics.session.FormSummary
import app.pacinglab.core.analytics.session.RunningForm
import app.pacinglab.core.analytics.session.SessionAnalysis
import app.pacinglab.core.analytics.session.SessionAnalyzer
import app.pacinglab.core.analytics.session.SessionClassifier
import app.pacinglab.core.analytics.session.SessionPart
import app.pacinglab.core.analytics.session.Split
import app.pacinglab.core.analytics.session.Splits
import app.pacinglab.core.analytics.workout.SpeedCleaning
import app.pacinglab.core.analytics.workout.StreamCleaning
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.EffortRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.weather.SessionWeatherRepository
import app.pacinglab.core.data.weather.SessionWeatherState
import app.pacinglab.core.model.Coordinates
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
import kotlinx.coroutines.flow.onStart
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
 * @property decoupling pace to heart rate decoupling of a steady long run recorded as one workout.
 * @property form cadence and stride length of a run with step counts; null otherwise.
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
    val decoupling: Decoupling? = null,
    val form: RunningFormView? = null,
)

/**
 * Cadence and stride length of a run: over its running minutes, and over its reps on their own when it
 * has some.
 */
data class RunningFormView(val overall: FormSummary, val reps: FormSummary?, val minutes: List<FormMinute>)

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
     * @property focus the scope to show first: a part when the screen was opened for one of its workouts.
     * @property weather the weather during the session; null while it is looked up.
     */
    data class Loaded(
        val session: SessionItem,
        val scopes: List<DetailScope>,
        val zonesAvailable: Boolean,
        val efforts: Map<String, Int> = emptyMap(),
        val speedByDefault: Boolean = false,
        val focus: Int = 0,
        val weather: SessionWeatherState? = null,
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
    private val weatherRepository: SessionWeatherRepository,
) : ViewModel() {
    private val analysed = combine(
        repository.observeSessions(),
        trainingLoad.anchors,
        preferences.preferences.map { it.sex.weighting }.distinctUntilChanged(),
    ) { sessions, timeline, weighting ->
        // The id may also name one workout of a session (from Performance): show that workout's tab first.
        val session = sessions.firstOrNull { it.id == sessionId } ?: sessions.firstOrNull { s -> s.workouts.any { it.id == sessionId } }
        Triple(session, timeline, weighting)
    }
        .mapLatest { (session, timeline, weighting) ->
            if (session == null) {
                SessionUiState.NotFound
            } else {
                val part = session.workouts.indexOfFirst { it.id == sessionId }
                load(session, timeline, weighting).let {
                    if (session.workouts.size > 1 && part >= 0 &&
                        session.id != sessionId
                    ) {
                        it.copy(focus = part + 1)
                    } else {
                        it
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)

    /** The weather of the session, looked up once its first workout and route are known. */
    private val weather = analysed
        .map { state -> (state as? SessionUiState.Loaded)?.scopes?.first() }
        .distinctUntilChanged { a, b -> a?.workoutIds == b?.workoutIds }
        .mapLatest { scope ->
            scope?.let {
                val start = it.routes.firstOrNull()?.takeIf { route -> !route.isEmpty() }
                val place = start?.let { route -> Coordinates(route.latitudes.first(), route.longitudes.first()) }
                weatherRepository.weatherOf(it.workoutIds.first(), place, it.start, it.end)
            }
        }
        .onStart { emit(null) }

    // Ratings and preferences join after the analysis so that rating a session does not analyse it again.
    val state: StateFlow<SessionUiState> = combine(
        analysed,
        ratings.observe(),
        preferences.preferences.map { it.speedByDefault }.distinctUntilChanged(),
        weather,
    ) { state, rated, speed, conditions ->
        if (state is SessionUiState.Loaded) {
            val ids = state.scopes.first().workoutIds.toSet()
            state.copy(efforts = rated.filterKeys { it in ids }, speedByDefault = speed, weather = conditions)
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

    private suspend fun load(session: TrainingSession, timeline: AnchorTimeline, weighting: BanisterWeighting): SessionUiState.Loaded {
        val parts = session.workouts.map { SessionPart(it, repository.streams(it)) }
        val anchors = timeline.at(TrainingLoads.dateOf(session.workouts.first(), ZoneId.systemDefault()))
        val zoneSets = anchors?.let { listOf(Zones.of(ZoneModel.LTHR_FIVE, it), Zones.of(ZoneModel.POLARIZED_THREE, it)) }.orEmpty()
        val five = zoneSets.firstOrNull()
        val analysis = SessionAnalyzer.analyze(parts, zoneSets, anchors, weighting)
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
        ).let { scope ->
            parts.singleOrNull()?.let { scope.copy(splits = it.splits(scope.analysis), decoupling = it.decoupling()) } ?: scope
        }.copy(form = form(parts, analysis))
        val scopes = if (parts.size ==
            1
        ) {
            listOf(whole)
        } else {
            listOf(whole) + parts.map { it.scope(zoneSets, anchors, weighting, it.roleIn(analysis)) }
        }
        return SessionUiState.Loaded(session = item, scopes = scopes, zonesAvailable = zoneSets.isNotEmpty())
    }

    /** Warm-up or cool-down when the session's analysis took this whole part for one. */
    private fun SessionPart.roleIn(analysis: SessionAnalysis): PhaseKind? = analysis.phases.firstOrNull {
        it.startMillis == workout.start.toEpochMilli() && it.endMillis == workout.end.toEpochMilli()
    }?.kind?.takeIf { it == PhaseKind.WARM_UP || it == PhaseKind.COOL_DOWN }

    private fun SessionPart.scope(zoneSets: List<ZoneSet>, anchors: HeartRateAnchors?, weighting: BanisterWeighting, role: PhaseKind?) =
        DetailScope(
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
            analysis = SessionAnalyzer.analyze(listOf(this), zoneSets, anchors, weighting),
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
            scope.copy(splits = splits(scope.analysis), kind = kind, decoupling = decoupling(), form = form(listOf(this), scope.analysis))
        }

    /**
     * Cadence and stride of the running in [parts]: every running minute, and the reps on their own (total
     * steps and distance over their time) when the session has them.
     */
    private fun form(parts: List<SessionPart>, analysis: SessionAnalysis): RunningFormView? {
        val runs = parts.filter { it.workout.sport == Sport.RUN && it.streams.steps.size > 1 }
        if (runs.isEmpty()) return null
        val minutes = runs.flatMap { part ->
            val recorded = if (part.streams.speed.size > 0) part.streams.speed else part.streams.route.speeds()
            val speed = SpeedCleaning.dropImplausible(recorded, SpeedCleaning.maxPlausibleSpeed(Sport.RUN))
            RunningForm.minutes(part.streams.steps, speed, part.workout.start.toEpochMilli(), part.workout.end.toEpochMilli())
        }
        val overall = RunningForm.summary(minutes) ?: return null
        var steps = 0.0
        var millis = 0L
        var meters = 0.0
        for (phase in analysis.phases.filter { it.kind == PhaseKind.WORK }) {
            val part =
                runs.firstOrNull {
                    phase.startMillis >= it.workout.start.toEpochMilli() && phase.endMillis <= it.workout.end.toEpochMilli()
                }
                    ?: continue
            steps += RunningForm.stepsBetween(part.streams.steps, phase.startMillis, phase.endMillis)
            millis += phase.durationMillis
            meters += phase.distanceMeters ?: 0.0
        }
        val reps = if (millis >= MIN_REP_MILLIS) RunningForm.fromTotals(steps, millis, meters) else null
        return RunningFormView(overall, reps, minutes)
    }

    /** Decoupling of a run, from GPS where there is a track and from the watch's speed otherwise. */
    private fun SessionPart.decoupling(): Decoupling? {
        if (workout.sport != Sport.RUN) return null
        val gps = StreamCleaning.movingSpeeds(streams.route, StreamCleaning.maxStepSpeed(Sport.RUN))
        val speed = if (gps.size > 0) gps else streams.speed
        return AerobicDecoupling.of(streams.heartRate, speed, workout.start.toEpochMilli(), workout.end.toEpochMilli())
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

        /** Reps shorter than this in all are too few step records to say anything on their own. */
        const val MIN_REP_MILLIS = 3 * 60_000L
    }
}
