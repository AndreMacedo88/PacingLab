package app.pacinglab.widget

import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.TrainingStatusKind
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.recovery.NightlyMetric
import app.pacinglab.core.analytics.recovery.NightlyRange
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.ReadinessLevel
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.analytics.recovery.SleepRating
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.data.repository.CardSpec
import app.pacinglab.core.data.repository.Dashboard
import app.pacinglab.core.data.repository.DashboardRepository
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.PerformanceRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.repository.TrainingStatusRepository
import app.pacinglab.core.data.repository.UserPreferencesRepository
import app.pacinglab.core.data.repository.WidgetChart
import app.pacinglab.core.model.RangeStanding
import app.pacinglab.core.ui.WidgetMeasure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** One of last night's vitals for the widget's panel; [standing] is null while its range is still being learned. */
data class WidgetVital(val metric: NightlyMetric, val value: Double, val standing: RangeStanding?)

/**
 * What the widget can show; each value is null when it is not known (shown as a dash) or not asked for.
 *
 * @property vitals last night's vitals that have a value, in the order of the Today card.
 * @property history the last seven days of [chart], oldest first.
 */
data class WidgetValues(
    val readiness: Int? = null,
    val readinessLevel: ReadinessLevel? = null,
    val sleepScore: Int? = null,
    val sleepRating: SleepRating? = null,
    val form: Int? = null,
    val hrv: Double? = null,
    val hrvStanding: RangeStanding? = null,
    val restingHr: Double? = null,
    val restingStanding: RangeStanding? = null,
    val asleepMillis: Long? = null,
    val loadWeek: Double? = null,
    val vo2max: Double? = null,
    val status: TrainingStatusKind? = null,
    val vitals: List<WidgetVital> = emptyList(),
    val chart: WidgetChart = WidgetChart.NONE,
    val history: List<Double?> = emptyList(),
) {
    val vitalsJudged: Int get() = vitals.count { it.standing != null }
    val vitalsInRange: Int get() = vitals.count { it.standing == RangeStanding.IN_RANGE }

    /** Two or more vitals outside their range at once ([Vitals.alert]). */
    val vitalsAlert: Boolean get() = vitals.count { it.standing != null && it.standing != RangeStanding.IN_RANGE } >= Vitals.ALERT_FROM

    /** A chart to draw: one was chosen and some of its days have a value. */
    val charted: Boolean get() = chart != WidgetChart.NONE && history.any { it != null }
}

/** The measures the athlete chose, in order, with their values. */
data class WidgetSnapshot(val measures: List<WidgetMeasure>, val values: WidgetValues)

/** Gathers the widget's values from the same repositories the screens use, only those it shows. */
class WidgetData @Inject constructor(
    private val nights: NightRepository,
    private val trainingLoad: TrainingLoadRepository,
    private val training: TrainingStatusRepository,
    private val performance: PerformanceRepository,
    private val dashboards: DashboardRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    /** The widget's contents, again whenever the chosen measures, the chart or the data behind them change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<WidgetSnapshot> = combine(
        dashboards.layout(Dashboard.WIDGET).map { layout ->
            layout.arrange(WidgetMeasure.entries.map { CardSpec(it.id, it.hiddenByDefault) })
                .filter { it.visible }
                .mapNotNull { WidgetMeasure.of(it.id) }
        },
        preferences.preferences.map { it.widgetChart },
    ) { measures, chart -> measures to chart }
        .distinctUntilChanged()
        .flatMapLatest { (measures, chart) ->
            combine(
                nights.observeSleep(),
                trainingLoad.observeWorkoutLoads(),
                if (WidgetMeasure.VO2MAX in measures) performance.observeVo2Max().map { it.trend?.current?.value } else flowOf(null),
                if (WidgetMeasure.STATUS in measures) training.observeStatus().map { it?.kind } else flowOf(null),
            ) { sleep, loads, vo2max, status -> WidgetSnapshot(measures, values(measures.toSet(), chart, sleep, loads, vo2max, status)) }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private fun values(
        shown: Set<WidgetMeasure>,
        chart: WidgetChart,
        sleep: SleepHistory,
        loads: List<WorkoutLoad>,
        vo2max: Double?,
        status: TrainingStatusKind?,
    ): WidgetValues {
        val today = LocalDate.now(clock)
        val form = FitnessModel.series(TrainingLoads.daily(loads), until = today)
        val formByDate = form.associateBy { it.date }
        val readiness = Readiness.of(sleep.nights, formByDate[today], today, sleep.scores)
        val night = sleep.nights.lastOrNull { it.date == today }
        val score = sleep.scores[today]
        fun standing(metric: NightlyMetric, value: Double?) = value?.let { NightlyRange.of(sleep.nights, metric, today)?.standing(it) }
        val byDate = sleep.nights.associateBy { it.date }
        val history = (DAYS - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            when (chart) {
                WidgetChart.READINESS -> Readiness.of(sleep.nights, formByDate[day], day, sleep.scores).score?.toDouble()
                WidgetChart.SLEEP_SCORE -> sleep.scores[day]?.score?.toDouble()
                WidgetChart.HRV -> byDate[day]?.hrvMs
                WidgetChart.RESTING_HR -> byDate[day]?.restingHr
                WidgetChart.FORM -> formByDate[day]?.form
                WidgetChart.TIME_ASLEEP -> byDate[day]?.asleepMillis?.let { it / HOUR_MILLIS }
                WidgetChart.NONE -> null
            }
        }
        val vitals = if (WidgetMeasure.VITALS in shown) Vitals.of(sleep.nights, today) else null
        return WidgetValues(
            readiness = readiness.score,
            readinessLevel = readiness.level,
            sleepScore = score?.score,
            sleepRating = score?.rating,
            form = form.lastOrNull()?.form?.roundToInt(),
            hrv = night?.hrvMs,
            hrvStanding = standing(NightlyMetric.HRV, night?.hrvMs),
            restingHr = night?.restingHr,
            restingStanding = standing(NightlyMetric.RESTING_HR, night?.restingHr),
            asleepMillis = night?.asleepMillis,
            loadWeek = if (WidgetMeasure.LOAD_WEEK in shown) TrainingLoads.weeks(loads, today, 1).first().load else null,
            vo2max = vo2max,
            status = status,
            vitals = vitals?.signs.orEmpty().mapNotNull { sign -> sign.value?.let { WidgetVital(sign.metric, it, sign.standing) } },
            chart = chart,
            history = if (chart == WidgetChart.NONE) emptyList() else history,
        )
    }

    private companion object {
        const val DAYS = 7
        const val HOUR_MILLIS = 3_600_000.0
    }
}
