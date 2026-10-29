package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.geo.speeds
import app.pacinglab.core.analytics.intervals.SprintDetection
import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.performance.AerobicDecoupling
import app.pacinglab.core.analytics.performance.AerobicEfficiency
import app.pacinglab.core.analytics.performance.BestEfforts
import app.pacinglab.core.analytics.performance.CriticalSpeed
import app.pacinglab.core.analytics.performance.CriticalSpeedGap
import app.pacinglab.core.analytics.performance.CriticalSpeedResult
import app.pacinglab.core.analytics.performance.DecouplingPoint
import app.pacinglab.core.analytics.performance.EfficiencyDay
import app.pacinglab.core.analytics.performance.EfficiencyPoint
import app.pacinglab.core.analytics.performance.EffortBests
import app.pacinglab.core.analytics.performance.EffortSource
import app.pacinglab.core.analytics.performance.FitbitDailyVo2Max
import app.pacinglab.core.analytics.performance.FitbitVo2Max
import app.pacinglab.core.analytics.performance.MarathonShape
import app.pacinglab.core.analytics.performance.MarathonShapes
import app.pacinglab.core.analytics.performance.RacePrediction
import app.pacinglab.core.analytics.performance.RacePredictions
import app.pacinglab.core.analytics.performance.RunDistance
import app.pacinglab.core.analytics.performance.RunEfforts
import app.pacinglab.core.analytics.performance.RunVo2Max
import app.pacinglab.core.analytics.performance.TrainingPaceCalculator
import app.pacinglab.core.analytics.performance.TrainingPaces
import app.pacinglab.core.analytics.performance.Vo2MaxPoint
import app.pacinglab.core.analytics.performance.Vo2MaxSource
import app.pacinglab.core.analytics.performance.Vo2MaxTrend
import app.pacinglab.core.analytics.workout.StreamCleaning
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every VO2max value with its source, and the trend through them.
 *
 * @property points oldest first; PacingLab's estimates are one per day, combined from that day's runs.
 */
data class Vo2MaxHistory(val points: List<Vo2MaxPoint>, val trend: Vo2MaxTrend?) {
    companion object {
        val EMPTY = Vo2MaxHistory(emptyList(), null)
    }
}

/**
 * Aerobic efficiency of every run with enough steady easy running, and its six-week trend.
 *
 * @property referenceHr the heart rate today's threshold puts the reference at, or null without one.
 */
data class EfficiencyHistory(val points: List<EfficiencyPoint>, val trend: List<EfficiencyDay>, val referenceHr: Int?) {
    companion object {
        val EMPTY = EfficiencyHistory(emptyList(), emptyList(), null)
    }
}

/**
 * What the athlete's runs say about racing.
 *
 * @property allTime best efforts over every run.
 * @property recent best efforts of the last [CriticalSpeed.WINDOW_DAYS] days.
 * @property vo2max today's VO2max from the trend, which the predictions use.
 * @property decoupling every steady long run's decoupling, oldest first.
 * @property shape the marathon shape that tempers the predictions of long races; null without VO2max.
 * @property paces Daniels' training paces from today's VO2max.
 */
data class RunningPerformance(
    val allTime: EffortBests,
    val recent: EffortBests,
    val criticalSpeed: CriticalSpeedResult,
    val predictions: List<RacePrediction>,
    val vo2max: Double?,
    val decoupling: List<DecouplingPoint>,
    val shape: MarathonShape? = null,
    val paces: TrainingPaces? = null,
) {
    companion object {
        val EMPTY = RunningPerformance(
            EffortBests.EMPTY,
            EffortBests.EMPTY,
            CriticalSpeedResult(null, CriticalSpeedGap.NO_SHORT_EFFORT, emptyList()),
            emptyList(),
            null,
            emptyList(),
        )
    }
}

/**
 * Performance measures: VO2max from tests, from Fitbit and from PacingLab's own run estimates; aerobic
 * efficiency; best efforts, critical speed, race predictions and aerobic decoupling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PerformanceRepository @Inject constructor(
    private val workouts: WorkoutStore,
    private val measurements: MeasurementStore,
    trainingLoad: TrainingLoadRepository,
    dailyVitals: DailyVitalsStore,
    private val clock: Clock,
) {
    private class Cached(val value: Double?, val windows: Int)

    private data class Key(val workoutId: String, val lastModified: Instant, val anchors: HeartRateAnchors?)

    private val estimates = ConcurrentHashMap<Key, Cached>()

    private val fitbitDaily: Flow<List<FitbitDailyVo2Max>> = dailyVitals.observe()
        .map { days -> days.mapNotNull { day -> day.vo2max?.let { v -> day.vo2maxVariance?.let { FitbitDailyVo2Max(day.date, v, it) } } } }
        .distinctUntilChanged()

    private val history: Flow<Vo2MaxHistory> = combine(
        workouts.observeAll(),
        trainingLoad.anchors,
        measurements.observeMeasurements(MeasurementKind.VO2_MAX, Instant.EPOCH, FAR_FUTURE),
        fitbitDaily,
    ) { all, timeline, recorded, daily -> VoInputs(all, timeline, recorded, daily) }
        .mapLatest { (all, timeline, recorded, daily) ->
            val runs = TrainingSessions.removeDuplicates(all.filter { it.sport == Sport.RUN && TrainingLoads.counts(it) })
            val byDay = runs.mapNotNull { workout ->
                val date = TrainingLoads.dateOf(workout, clock.zone)
                val anchors = timeline.at(date)
                val key = Key(workout.id, workout.lastModified, anchors)
                val cached = estimates[key] ?: run {
                    val streams = workouts.cleanStreams(workout)
                    // Runs before August 2026 came without a speed stream; their GPS track gives the same estimates.
                    val speed = if (streams.speed.size > 0) streams.speed else streams.route.speeds()
                    val estimate = anchors?.let {
                        RunVo2Max.of(streams.heartRate, speed, workout.start.toEpochMilli(), workout.end.toEpochMilli(), it)
                    }
                    Cached(estimate?.value, estimate?.windows ?: 0).also { estimates[key] = it }
                }
                cached.value?.let { Triple(date, it, cached.windows) }
            }.groupBy { it.first }
            val own = byDay.map { (date, day) ->
                val windows = day.sumOf { it.third }
                // Overlapping stretches of one day are not independent: a well-covered day counts as one estimate.
                Vo2MaxPoint(
                    date,
                    day.sumOf { it.second * it.third } / windows,
                    Vo2MaxSource.PACINGLAB,
                    weight = minOf(
                        1.0,
                        windows / FULL_WEIGHT_WINDOWS,
                    ),
                )
            }
            val other = recorded.map { m ->
                Vo2MaxPoint(
                    date = m.time.atZone(m.zoneOffset ?: clock.zone).toLocalDate(),
                    value = m.value,
                    source = if (m.origin == TEST_ORIGIN) Vo2MaxSource.TEST else Vo2MaxSource.FITBIT,
                )
            }
            val (fitbit, tests) = other.partition { it.source == Vo2MaxSource.FITBIT }
            val points = (own + tests + FitbitVo2Max.points(fitbit, daily)).sortedBy { it.date }
            Vo2MaxHistory(points, Vo2MaxTrend.of(points, LocalDate.now(clock)))
        }
        .flowOn(Dispatchers.Default)

    fun observeVo2Max(): Flow<Vo2MaxHistory> = history

    private data class VoInputs(
        val workouts: List<Workout>,
        val timeline: AnchorTimeline,
        val recorded: List<Measurement>,
        val daily: List<FitbitDailyVo2Max>,
    )

    private val efficiencies = ConcurrentHashMap<Key, Cached>()

    private val efficiency: Flow<EfficiencyHistory> = combine(workouts.observeAll(), trainingLoad.anchors) { all, timeline ->
        all to
            timeline
    }
        .mapLatest { (all, timeline) ->
            val runs = TrainingSessions.removeDuplicates(all.filter { it.sport == Sport.RUN && TrainingLoads.counts(it) })
            val points = runs.mapNotNull { workout ->
                val date = TrainingLoads.dateOf(workout, clock.zone)
                val anchors = timeline.at(date) ?: return@mapNotNull null
                val key = Key(workout.id, workout.lastModified, anchors)
                val cached = efficiencies[key] ?: run {
                    val streams = workouts.cleanStreams(workout)
                    val speed = if (streams.speed.size > 0) streams.speed else streams.route.speeds()
                    val estimate = AerobicEfficiency.of(
                        streams.heartRate,
                        speed,
                        workout.start.toEpochMilli(),
                        workout.end.toEpochMilli(),
                        anchors.thresholdHr,
                    )
                    Cached(estimate?.speedAtReference, estimate?.windows ?: 0).also { efficiencies[key] = it }
                }
                cached.value?.let { EfficiencyPoint(date, it, cached.windows) }
            }.sortedBy { it.date }
            val today = LocalDate.now(clock)
            EfficiencyHistory(
                points,
                AerobicEfficiency.trend(points, today),
                timeline.at(today)?.thresholdHr?.let(AerobicEfficiency::referenceHr),
            )
        }
        .flowOn(Dispatchers.Default)

    fun observeEfficiency(): Flow<EfficiencyHistory> = efficiency

    private class RunAnalysis(val efforts: RunEfforts, val decoupling: Double?)

    private val runAnalyses = ConcurrentHashMap<Key, RunAnalysis>()

    private val running: Flow<RunningPerformance> = combine(workouts.observeAll(), trainingLoad.anchors, history) { all, timeline, vo2 ->
        Triple(all, timeline, vo2)
    }
        .mapLatest { (all, timeline, vo2) ->
            val today = LocalDate.now(clock)
            val runs = TrainingSessions.removeDuplicates(all.filter { it.sport == Sport.RUN && TrainingLoads.counts(it) })
            val analysed = runs.map { workout ->
                val date = TrainingLoads.dateOf(workout, clock.zone)
                val anchors = timeline.at(date)
                val key = Key(workout.id, workout.lastModified, anchors)
                date to workout.id to (runAnalyses[key] ?: analyse(workout, date, anchors).also { runAnalyses[key] = it })
            }
            val efforts = analysed.map { it.second.efforts }
            val recentFrom = today.minusDays(CriticalSpeed.WINDOW_DAYS)
            val criticalSpeed = CriticalSpeed.of(efforts, today)
            val vo2max = vo2.trend?.current?.value
            val shape = vo2max?.let { v ->
                val distances = runs.mapNotNull { run ->
                    run.summary.distanceMeters?.let { RunDistance(TrainingLoads.dateOf(run, clock.zone), it) }
                }
                MarathonShapes.of(distances, v, today)
            }
            RunningPerformance(
                allTime = BestEfforts.best(efforts),
                recent = BestEfforts.best(analysed.filter { it.first.first > recentFrom }.map { it.second.efforts }),
                criticalSpeed = criticalSpeed,
                predictions = RacePredictions.of(vo2max, criticalSpeed.fit, shape),
                vo2max = vo2max,
                shape = shape,
                paces = vo2max?.let(TrainingPaceCalculator::of),
                decoupling = analysed.mapNotNull { (dated, analysis) ->
                    analysis.decoupling?.let { DecouplingPoint(dated.first, dated.second, it) }
                }.sortedBy { it.date },
            )
        }
        .flowOn(Dispatchers.Default)

    fun observeRunning(): Flow<RunningPerformance> = running

    /**
     * Efforts come from the GPS track where there is one. Without it the watch's own speed is used, except
     * in sprint sessions: at sprint speed it reads far too high.
     */
    private suspend fun analyse(workout: Workout, date: LocalDate, anchors: HeartRateAnchors?): RunAnalysis {
        val streams = workouts.cleanStreams(workout)
        val start = workout.start.toEpochMilli()
        val end = workout.end.toEpochMilli()
        val gps = StreamCleaning.movingSpeeds(streams.route, StreamCleaning.maxStepSpeed(Sport.RUN))
        val (source, speed) = when {
            gps.size > 0 -> EffortSource.GPS to gps
            streams.speed.size > 0 && SprintDetection.detect(streams.speed, start, end) == null -> EffortSource.WATCH to streams.speed
            else -> return RunAnalysis(RunEfforts.EMPTY, null)
        }
        return RunAnalysis(
            BestEfforts.of(workout.id, date, source, speed, streams.heartRate, start, end, anchors),
            AerobicDecoupling.of(streams.heartRate, speed, start, end)?.percent,
        )
    }

    /** Records a lab or field test; a second test on the same day replaces the first. */
    suspend fun saveTest(date: LocalDate, value: Double) {
        val zone = clock.zone
        measurements.saveMeasurements(
            listOf(
                Measurement(
                    sourceRecordId = testId(date),
                    kind = MeasurementKind.VO2_MAX,
                    time = date.atTime(LocalTime.NOON).atZone(zone).toInstant(),
                    zoneOffset = date.atTime(LocalTime.NOON).atZone(zone).offset,
                    value = value,
                    origin = TEST_ORIGIN,
                ),
            ),
        )
    }

    suspend fun deleteTest(date: LocalDate) = measurements.delete(listOf(testId(date)))

    private fun testId(date: LocalDate) = "pacinglab:vo2max-test:$date"

    companion object {
        /** Origin of values the athlete entered, as opposed to those an app wrote to Health Connect. */
        const val TEST_ORIGIN = "app.pacinglab.test"

        /** Steady stretches at which a day's own estimate counts fully. */
        private const val FULL_WEIGHT_WINDOWS = 20.0

        private val FAR_FUTURE: Instant = Instant.parse("2200-01-01T00:00:00Z")
    }
}
