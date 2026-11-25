package app.pacinglab.core.data.home

import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.alertness.AlertnessLevel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.load.WeeklyTargets
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.performance.MarathonShape
import app.pacinglab.core.analytics.performance.RaceDistance
import app.pacinglab.core.analytics.performance.RacePredictions
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.recovery.VitalSign
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.analytics.strain.DaytimeStrain
import app.pacinglab.core.analytics.strain.StrainDay
import app.pacinglab.core.analytics.strain.StrainLevel
import app.pacinglab.core.data.repository.AlertnessRepository
import app.pacinglab.core.data.repository.EffortRepository
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PerformanceRepository
import app.pacinglab.core.data.repository.SessionKindRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WellnessRepository
import app.pacinglab.core.data.repository.WorkoutRepository
import app.pacinglab.core.database.store.StillHeartRateStore
import app.pacinglab.core.model.RangeStanding
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The JSON PacingLab sends to Home Assistant's webhook. */
fun interface HomeAssistantValues {
    suspend fun build(): String
}

/**
 * The newest session that counts as training, as Home Assistant shows it.
 *
 * @property title the run's kind, or the sport of any other session.
 */
data class LastSession(val title: String, val end: Instant, val load: Double?, val movingMillis: Long, val distanceMeters: Double?)

/**
 * Everything the webhook's values come from, gathered by [HomeAssistantPayload].
 *
 * @property shape the marathon shape at today's VO2max, which the app's race times take into account.
 * @property sleepNeedMillis the sleep need going into tonight.
 * @property runningTolerance the running increase the athlete allows, as a factor (1.3 for 30 %).
 * @property toDo what the To do card on Today waits for, in Home Assistant's words.
 */
data class HomeAssistantInputs(
    val today: LocalDate,
    val now: Instant,
    val sleep: SleepHistory,
    val form: List<FormDay>,
    val strain: StrainDay? = null,
    val vo2max: Double? = null,
    val shape: MarathonShape? = null,
    val status: TrainingStatus? = null,
    val alertness: AlertnessHistory? = null,
    val sleepNeedMillis: Long? = null,
    val loads: List<WorkoutLoad> = emptyList(),
    val runningTolerance: Double = WeeklyTargets.TOLERANCE,
    val lastSession: LastSession? = null,
    val toDo: List<String> = emptyList(),
)

/**
 * The values PacingLab sends to Home Assistant, keyed as in [HomeAssistantYaml.VALUES]. A value not known yet
 * is sent as null, which leaves its sensor unavailable. Values of the day (readiness, last night, vitals,
 * alertness) are sent only once last night has arrived, so that Home Assistant never shows yesterday's
 * readiness, or one worked out without the night, as today's. Words are the app's own.
 */
object HomeAssistantReport {
    fun json(inputs: HomeAssistantInputs): String {
        val today = inputs.today
        val night = inputs.sleep.nights.lastOrNull()?.takeIf { it.date == today }
        val formToday = inputs.form.lastOrNull { it.date == today }
        val readiness = night?.let { Readiness.of(inputs.sleep.nights, formToday, today, inputs.sleep.scores) }
        val score = night?.let { inputs.sleep.scores[today] }
        val vitals = night?.let { Vitals.of(inputs.sleep.nights, today) }?.takeIf { it.judged > 0 }
        val alertness = inputs.alertness?.days?.get(today)?.takeIf { night != null && !it.assumed }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = TrainingLoads.weeks(inputs.loads, until = today, count = 1).first()
        val band = WeeklyTargets.loadBands(inputs.form, listOf(monday))[monday]
        val running = WeeklyTargets.runningWeeks(
            inputs.loads.filter { it.sport == Sport.RUN }.mapNotNull { load -> load.distanceMeters?.let { load.date to it } },
            until = today,
            count = 1,
            tolerance = inputs.runningTolerance,
        ).first()
        val last = inputs.lastSession
        return JSONObject()
            .put("readiness", readiness?.score)
            .put("readiness_level", readiness?.level?.let(::word))
            .put("sleep_score", score?.score)
            .put("sleep_rating", score?.rating?.let(::word))
            .put("asleep_hours", night?.let { tenths(it.asleepMillis / HOUR) })
            .put("asleep_minutes", night?.let { (it.asleepMillis / MINUTE).roundToInt() })
            .put("sleep_health", night?.let { inputs.sleep.health[today]?.score })
            .put("sleep_debt_hours", night?.let { inputs.sleep.debts[today]?.let { tenths(it.millis / HOUR) } })
            .put("sleep_need_hours", inputs.sleepNeedMillis?.let { tenths(it / HOUR) })
            .put("hrv", night?.hrvMs?.let { round(it) })
            .put("resting_hr", night?.restingHr?.let { round(it) })
            .put("vitals_in_range", vitals?.let { it.judged - it.outside.size })
            .put("vitals_judged", vitals?.judged)
            .put("vitals_outside", vitals?.let { JSONArray(it.outside.map(::word)) })
            .put("vitals_alert", vitals?.alert)
            .put("alertness", alertness?.score?.roundToInt())
            .put("alertness_level", alertness?.level?.let(::word))
            .put("sharpest_from", alertness?.best?.first?.toString())
            .put("sharpest_until", alertness?.best?.second?.toString())
            .put("strain", inputs.strain?.score)
            .put("strain_level", inputs.strain?.level?.let(::word))
            .put("form", formToday?.let { round(it.form) })
            .put("fitness", formToday?.let { round(it.fitness) })
            .put("fatigue", formToday?.let { round(it.fatigue) })
            .put("week_load", if (inputs.loads.isEmpty()) null else round(week.load))
            .put("week_load_low", band?.let { round(it.low) })
            .put("week_load_high", band?.let { round(it.high) })
            .put("week_running_km", if (inputs.loads.isEmpty()) null else tenths(running.km))
            .put("week_running_tolerance_km", running.toleranceKm?.let(::tenths))
            .put("vo2max", inputs.vo2max?.let(::tenths))
            .put("vo2max_change", inputs.status?.vo2maxChange?.let(::tenths))
            .put("race_5k", race5k(inputs)?.let(::clock))
            .put("training_status", inputs.status?.kind?.let(::word))
            .put("last_workout", last?.title)
            .put("last_workout_end", last?.end?.toString())
            .put("last_workout_load", last?.load?.let { round(it) })
            .put("last_workout_minutes", last?.let { (it.movingMillis / MINUTE).roundToInt() })
            .put("last_workout_km", last?.distanceMeters?.let { tenths(it / 1_000) })
            .put("to_do", inputs.toDo.size)
            .put("to_do_items", JSONArray(inputs.toDo))
            .put("updated", inputs.now.toString())
            .toString()
    }

    /** The 5 km time as the app shows it: from VO2max, allowing for the marathon shape. */
    private fun race5k(inputs: HomeAssistantInputs): Double? =
        RacePredictions.of(inputs.vo2max, null, inputs.shape).firstOrNull { it.distance == RaceDistance.K5 }?.fromVo2Max

    fun word(level: ReadinessLevel): String = when (level) {
        ReadinessLevel.READY -> "Ready to train"
        ReadinessLevel.MODERATE -> "Train with care"
        ReadinessLevel.LOW -> "Take it easy"
    }

    fun word(rating: SleepRating): String = when (rating) {
        SleepRating.GOOD -> "Good"
        SleepRating.FAIR -> "Fair"
        SleepRating.POOR -> "Poor"
    }

    fun word(level: StrainLevel): String = when (level) {
        StrainLevel.CALM -> "Calm"
        StrainLevel.USUAL -> "Usual"
        StrainLevel.RAISED -> "Raised"
        StrainLevel.HIGH -> "High"
    }

    fun word(level: AlertnessLevel): String = when (level) {
        AlertnessLevel.ALERT -> "Alert"
        AlertnessLevel.REDUCED -> "Reduced"
        AlertnessLevel.LOW -> "Low"
    }

    fun word(kind: TrainingStatusKind): String = when (kind) {
        TrainingStatusKind.PRODUCTIVE -> "Productive"
        TrainingStatusKind.MAINTAINING -> "Maintaining"
        TrainingStatusKind.PEAKING -> "Peaking"
        TrainingStatusKind.RECOVERY -> "Recovery"
        TrainingStatusKind.UNPRODUCTIVE -> "Unproductive"
        TrainingStatusKind.DETRAINING -> "Detraining"
        TrainingStatusKind.OVERREACHING -> "Overreaching"
        TrainingStatusKind.STRAINED -> "Strained"
    }

    fun word(kind: SessionKind): String = when (kind) {
        SessionKind.SPRINTS -> "Sprint workout"
        SessionKind.VO2MAX_INTERVALS -> "VO2max intervals"
        SessionKind.THRESHOLD_INTERVALS -> "Threshold intervals"
        SessionKind.TEMPO_INTERVALS -> "Tempo intervals"
        SessionKind.INTERVALS -> "Interval workout"
        SessionKind.THRESHOLD_RUN -> "Threshold run"
        SessionKind.TEMPO_RUN -> "Tempo run"
        SessionKind.EASY_RUN -> "Easy run"
        SessionKind.LONG_RUN -> "Long run"
        SessionKind.RUN -> "Run"
    }

    fun word(sport: Sport): String = when (sport) {
        Sport.RUN -> "Run"
        Sport.WALK -> "Walk"
        Sport.RIDE -> "Ride"
        Sport.STRENGTH -> "Strength"
        Sport.OTHER -> "Workout"
    }

    /** A measure outside its range, such as "Resting HR above". */
    fun word(sign: VitalSign): String {
        val name = when (sign.metric) {
            NightlyMetric.HRV -> "HRV"
            NightlyMetric.RESTING_HR -> "Resting HR"
            NightlyMetric.BREATHING_RATE -> "Breathing rate"
            NightlyMetric.OXYGEN_SATURATION -> "SpO2"
            NightlyMetric.SKIN_TEMPERATURE -> "Skin temperature"
            NightlyMetric.SLEEPING_HR -> "Sleeping HR"
        }
        return if (sign.standing == RangeStanding.ABOVE) "$name above" else "$name below"
    }

    private fun tenths(value: Double) = round(value * 10) / 10

    /** A race time as m:ss, or h:mm:ss from an hour. */
    private fun clock(seconds: Double): String {
        val total = seconds.roundToLong()
        val h = total / 3_600
        val m = total % 3_600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    private const val HOUR = 3_600_000.0
    private const val MINUTE = 60_000.0

    /** To do items, as Home Assistant shows them. */
    const val TODO_MORNING = "Morning question"
    const val TODO_GYM = "Gym effort"
    const val TODO_TEST = "Reaction test"
    const val TODO_VALUES = "Heart rate values"
}

/** Gathers [HomeAssistantInputs] from the app's repositories and turns them into the webhook's JSON. */
class HomeAssistantPayload @Inject constructor(
    private val nights: NightRepository,
    private val loads: TrainingLoadRepository,
    private val status: TrainingStatusRepository,
    private val performance: PerformanceRepository,
    private val still: StillHeartRateStore,
    private val alertness: AlertnessRepository,
    private val workouts: WorkoutRepository,
    private val kinds: SessionKindRepository,
    private val wellness: WellnessRepository,
    private val efforts: EffortRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) : HomeAssistantValues {
    override suspend fun build(): String {
        val today = LocalDate.now(clock)
        val sleep = nights.observeSleep().first()
        val workoutLoads = loads.observeWorkoutLoads().first()
        val anchors = loads.anchors.first()
        val sessions = workouts.observeSessions().first()
        val prefs = preferences.preferences.first()
        val alertnessHistory = alertness.observe().first()
        val toDo = buildList {
            if (wellness.observeRested().first()[today] == null) add(HomeAssistantReport.TODO_MORNING)
            if (EffortRepository.toRate(sessions, efforts.observe().first(), today, clock.zone) != null) add(HomeAssistantReport.TODO_GYM)
            if (alertnessHistory.testDue) add(HomeAssistantReport.TODO_TEST)
            if (anchors.at(today) == null) add(HomeAssistantReport.TODO_VALUES)
        }
        val last = sessions.firstOrNull { session -> session.workouts.any(TrainingLoads::counts) }?.let { session ->
            val ids = session.workouts.map { it.id }.toSet()
            val scored = workoutLoads.filter { it.workoutId in ids }.mapNotNull { it.scores }
            LastSession(
                title = if (session.sport == Sport.RUN) {
                    HomeAssistantReport.word(kinds.kindOf(session, anchors))
                } else {
                    HomeAssistantReport.word(session.sport)
                },
                end = session.end,
                load = scored.takeIf { it.isNotEmpty() }?.sumOf { it.stressScore },
                movingMillis = session.workouts.sumOf { it.summary.durationMillis },
                // A gym session's distance is only its steps.
                distanceMeters = session.workouts.mapNotNull { it.summary.distanceMeters }
                    .takeIf { it.isNotEmpty() && session.sport in DISTANCE_SPORTS }?.sum(),
            )
        }
        val inputs = HomeAssistantInputs(
            today = today,
            now = clock.instant(),
            sleep = sleep,
            form = loads.observeFitness().first(),
            strain = DaytimeStrain.days(still.observeAll().first()).lastOrNull { it.score != null },
            vo2max = performance.observeVo2Max().first().trend?.current?.value,
            shape = performance.observeShape().first(),
            status = status.observeStatus().first(),
            alertness = alertnessHistory,
            sleepNeedMillis =
            prefs.sleepNeed?.toMillis() ?: sleep.nights.takeIf { it.isNotEmpty() }?.let { SleepScore.need(it, today.plusDays(1)) },
            loads = workoutLoads,
            runningTolerance = 1 + prefs.runningIncreasePercent / 100.0,
            lastSession = last,
            toDo = toDo,
        )
        return HomeAssistantReport.json(inputs)
    }

    private companion object {
        val DISTANCE_SPORTS = setOf(Sport.RUN, Sport.WALK, Sport.RIDE)
    }
}
