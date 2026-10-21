package app.pacinglab.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.WeekSummary
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.Sport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
)

sealed interface TodayUiState {
    data object Loading : TodayUiState

    /**
     * @property nights the nights of the last [TodayViewModel.NIGHT_DAYS] days, oldest first.
     * @property sleepScores the sleep score of each of those nights.
     * @property form the fitness model for the last [TodayViewModel.FORM_DAYS] days; empty without load.
     * @property weekLoads daily load from Monday to Sunday of the current week.
     * @property weekSessions training sessions this week; a warm-up and an interval workout make one.
     * @property heartRateValuesSet false until the athlete has entered a maximal heart rate.
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
    ) : TodayUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    trainingLoad: TrainingLoadRepository,
    nights: NightRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<TodayUiState> = combine(
        nights.observeNights(),
        trainingLoad.observeWorkoutLoads(),
        workouts.observeSessions(),
        trainingLoad.anchors,
    ) { allNights, loads, sessions, anchors -> Inputs(allNights, loads, sessions, anchors.at(LocalDate.now(clock)) != null) }
        .mapLatest { build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TodayUiState.Loading)

    private class Inputs(
        val nights: List<Night>,
        val loads: List<WorkoutLoad>,
        val sessions: List<TrainingSession>,
        val heartRateValuesSet: Boolean,
    )

    private suspend fun build(inputs: Inputs): TodayUiState.Loaded {
        val today = LocalDate.now(clock)
        val daily = TrainingLoads.daily(inputs.loads)
        val form = FitnessModel.series(daily, until = today)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return TodayUiState.Loaded(
            today = today,
            readiness = Readiness.of(inputs.nights, form.lastOrNull()?.takeIf { it.date == today }, today),
            nights = inputs.nights.filter { it.date > today.minusDays(NIGHT_DAYS.toLong()) },
            sleepScores = SleepScore.all(inputs.nights.filter { it.date > today.minusDays(NIGHT_DAYS + SCORE_HISTORY_DAYS) })
                .filterKeys { it > today.minusDays(NIGHT_DAYS.toLong()) },
            form = form.takeLast(FORM_DAYS),
            week = TrainingLoads.weeks(inputs.loads, until = today, count = 1).firstOrNull(),
            weekLoads = List(7) { daily[monday.plusDays(it.toLong())] ?: 0.0 },
            weekSessions = inputs.sessions.count { session ->
                session.workouts.any(TrainingLoads::counts) && !TrainingLoads.dateOf(session.workouts.first(), clock.zone).isBefore(monday)
            },
            lastWorkout = lastWorkout(inputs.sessions, inputs.loads),
            heartRateValuesSet = inputs.heartRateValuesSet,
        )
    }

    /** The newest session that counts as training; detected walks and rides do not. */
    private suspend fun lastWorkout(sessions: List<TrainingSession>, loads: List<WorkoutLoad>): LastWorkout? {
        val session = sessions.firstOrNull { s -> s.workouts.any(TrainingLoads::counts) } ?: return null
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
            routes = session.workouts.filter { it.summary.hasRoute }.map { workouts.streams(it.id).route }.filterNot { it.isEmpty() },
        )
    }

    companion object {
        const val NIGHT_DAYS = 14
        const val FORM_DAYS = 42

        /** Earlier nights the sleep scores need for need and regularity. */
        private const val SCORE_HISTORY_DAYS = 30L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
