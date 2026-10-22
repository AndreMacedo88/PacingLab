package app.pacinglab.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.SessionKindRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToInt

/** The latest training session, with what its card shows. */
data class LastWorkout(
    val sessionId: String,
    val titles: List<String>,
    val sport: Sport,
    val start: Instant,
    val zoneOffset: ZoneOffset?,
    val movingMillis: Long,
    val distanceMeters: Double?,
    val averageHr: Int?,
    val load: Double?,
    val routes: List<Route>,
    val heartRate: HeartRateSeries = HeartRateSeries.EMPTY,
    val zones: ZoneSet? = null,
    val kind: SessionKind? = null,
)

sealed interface TodayUiState {
    data object Loading : TodayUiState

    /**
     * @property readinessHistory readiness on each of the last [TodayViewModel.TILE_DAYS] days, oldest first,
     *   each from the data up to its own day; the last is [readiness]. Tiles take each day's normal ranges
     *   from it.
     * @property nights the nights of the last [TodayViewModel.NIGHT_DAYS] days, oldest first.
     * @property sleepScores the sleep score of each of those nights.
     * @property form the fitness model for the last [TodayViewModel.FORM_DAYS] days; empty without load.
     * @property weekLoads daily load from Monday to Sunday of the current week.
     * @property weekSessions training sessions this week; a warm-up and an interval workout make one.
     * @property heartRateValuesSet false until the athlete has entered a maximal heart rate.
     * @property rested how rested the athlete said they feel today, 1 to 5; null before they answer.
     */
    data class Loaded(
        val today: LocalDate,
        val readiness: Readiness,
        val nights: List<Night>,
        val sleepScores: Map<LocalDate, SleepScore>,
        val form: List<FormDay>,
        val week: WeekSummary?,
        val weekLoads: List<Double>,
        val weekSessions: Int,
        val lastWorkout: LastWorkout?,
        val heartRateValuesSet: Boolean,
        val readinessHistory: List<Readiness> = listOf(readiness),
        val rested: Int? = null,
    ) : TodayUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    trainingLoad: TrainingLoadRepository,
    nights: NightRepository,
    private val clock: Clock,
    private val scheduler: SyncScheduler,
    private val kinds: SessionKindRepository,
    private val wellness: WellnessRepository,
) : ViewModel() {
    val state: StateFlow<TodayUiState> = combine(
        nights.observeSleep(),
        trainingLoad.observeWorkoutLoads(),
        workouts.observeSessions(),
        trainingLoad.anchors,
        wellness.observeRested(),
    ) { sleep, loads, sessions, anchors, rested -> Inputs(sleep, loads, sessions, anchors, rested) }
        .mapLatest { build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState.Loading)

    val refreshing: StateFlow<Boolean> =
        scheduler.updating.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() = scheduler.syncNow()

    /** Records how rested the athlete feels today, or forgets the answer when they pick it again. */
    fun rate(rating: Int) {
        viewModelScope.launch {
            val today = LocalDate.now(clock)
            val current = (state.value as? TodayUiState.Loaded)?.rested
            if (current == rating) wellness.clearRested(today) else wellness.setRested(today, rating)
        }
    }

    private class Inputs(
        val sleep: SleepHistory,
        val loads: List<WorkoutLoad>,
        val sessions: List<TrainingSession>,
        val anchors: AnchorTimeline,
        val rested: Map<LocalDate, Int>,
    ) {
        val nights: List<Night> get() = sleep.nights
    }

    private suspend fun build(inputs: Inputs): TodayUiState.Loaded {
        val today = LocalDate.now(clock)
        val daily = TrainingLoads.daily(inputs.loads)
        val form = FitnessModel.series(daily, until = today)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val formByDate = form.associateBy { it.date }
        val history = (TILE_DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            Readiness.of(inputs.nights, formByDate[day], day, inputs.sleep.scores)
        }
        return TodayUiState.Loaded(
            today = today,
            readiness = history.last(),
            readinessHistory = history,
            nights = inputs.nights.filter { it.date > today.minusDays(NIGHT_DAYS.toLong()) },
            sleepScores = inputs.sleep.scores.filterKeys { it > today.minusDays(NIGHT_DAYS.toLong()) },
            form = form.takeLast(FORM_DAYS),
            week = TrainingLoads.weeks(inputs.loads, until = today, count = 1).firstOrNull(),
            weekLoads = List(7) { daily[monday.plusDays(it.toLong())] ?: 0.0 },
            weekSessions = inputs.sessions.count { session ->
                session.workouts.any(TrainingLoads::counts) && !TrainingLoads.dateOf(session.workouts.first(), clock.zone).isBefore(monday)
            },
            lastWorkout = lastWorkout(inputs.sessions, inputs.loads, inputs.anchors),
            heartRateValuesSet = inputs.anchors.at(today) != null,
            rested = inputs.rested[today],
        )
    }

    /** The newest session that counts as training; detected walks and rides do not. */
    private suspend fun lastWorkout(sessions: List<TrainingSession>, loads: List<WorkoutLoad>, anchors: AnchorTimeline): LastWorkout? {
        val session = sessions.firstOrNull { s -> s.workouts.any(TrainingLoads::counts) } ?: return null
        val streams = session.workouts.map { workouts.streams(it.id) }
        val byId = loads.associateBy { it.workoutId }
        val scored = session.workouts.mapNotNull { byId[it.id]?.scores }
        val withHr = session.workouts.filter { it.summary.averageHr != null }
        val hrMillis = withHr.sumOf { it.summary.durationMillis }
        return LastWorkout(
            sessionId = session.id,
            titles = session.workouts.mapNotNull { it.title?.takeIf(String::isNotBlank) }.distinct(),
            sport = session.sport,
            start = session.start,
            zoneOffset = session.workouts.first().zoneOffset,
            movingMillis = session.workouts.sumOf { it.summary.durationMillis },
            distanceMeters = session.workouts.mapNotNull { it.summary.distanceMeters }.takeIf { it.isNotEmpty() }?.sum(),
            averageHr = if (hrMillis == 0L) {
                null
            } else {
                (withHr.sumOf { it.summary.averageHr!!.toDouble() * it.summary.durationMillis } / hrMillis).roundToInt()
            },
            load = scored.takeIf { it.isNotEmpty() }?.sumOf { it.stressScore },
            routes = streams.map { it.route }.filterNot { it.isEmpty() },
            heartRate = HeartRateSeries.joined(streams.map { it.heartRate }),
            zones = anchors.at(TrainingLoads.dateOf(session.workouts.first(), clock.zone))?.let { Zones.of(ZoneModel.LTHR_FIVE, it) },
            kind = if (session.sport == Sport.RUN) kinds.kindOf(session, anchors) else null,
        )
    }

    companion object {
        /** Days the tiles' mini charts show, labelled with weekday initials. */
        const val TILE_DAYS = 7
        const val NIGHT_DAYS = 14
        const val FORM_DAYS = 42
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
