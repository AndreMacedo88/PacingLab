package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.geo.speeds
import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.BanisterWeighting
import app.pacinglab.core.analytics.load.EffortLoad
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.HeartRateLoad
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.workout.StreamCleaning
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.analytics.zones.HeartRatePeak
import app.pacinglab.core.analytics.zones.MaxHeartRateEstimate
import app.pacinglab.core.analytics.zones.ThresholdEffort
import app.pacinglab.core.analytics.zones.ThresholdHeartRateEstimate
import app.pacinglab.core.analytics.zones.TimeInZones
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.ZoneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Training load of every workout and the fitness, fatigue and form it adds up to. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrainingLoadRepository @Inject constructor(
    private val workouts: WorkoutStore,
    physiology: PhysiologyStore,
    nights: NightRepository,
    private val preferences: UserPreferencesRepository,
    private val ratings: EffortRepository,
    private val clock: Clock,
) {
    private class Cached(val scores: LoadScores?, val intensity: ZoneDistribution?)

    private data class CacheKey(
        val workoutId: String,
        val lastModified: Instant,
        val anchors: HeartRateAnchors?,
        val weighting: BanisterWeighting,
    )

    /** Scores survive recomposition and syncs; a workout is only recalculated when it or its anchors change. */
    private val cache = ConcurrentHashMap<CacheKey, Cached>()

    private class Peak(val bpm: Double?)

    /** Sustained peaks by workout and version, so that a sync only decodes what changed. */
    private val peaks = ConcurrentHashMap<Pair<String, Instant>, Peak>()

    /** Maximal heart rate estimated from the workouts that count as training. */
    val estimatedMax: Flow<MaxHeartRateEstimate?> = workouts.observeAll()
        .mapLatest { all ->
            val counted = TrainingSessions.removeDuplicates(all.filter(TrainingLoads::counts))
            val found = counted.mapNotNull { workout ->
                val key = workout.id to workout.lastModified
                val peak = peaks[key] ?: Peak(MaxHeartRateEstimate.sustainedPeak(workouts.heartRate(workout.id))).also { peaks[key] = it }
                peak.bpm?.let { HeartRatePeak(workout.id, TrainingLoads.dateOf(workout, clock.zone), it) }
            }
            MaxHeartRateEstimate.of(found, LocalDate.now(clock))
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private class Effort(val bpm: Double?)

    /** Threshold efforts by workout and version, like [peaks]. */
    private val efforts = ConcurrentHashMap<Pair<String, Instant>, Effort>()

    /** The hardest continuous half hour of every run long enough to hold one. */
    private val thresholdEfforts: Flow<List<ThresholdEffort>> = workouts.observeAll()
        .mapLatest { all ->
            val runs = all.filter {
                it.sport == Sport.RUN && TrainingLoads.counts(it) &&
                    Duration.between(it.start, it.end).seconds >= ThresholdHeartRateEstimate.EFFORT_SECONDS
            }
            TrainingSessions.removeDuplicates(runs).mapNotNull { workout ->
                val key = workout.id to workout.lastModified
                val effort = efforts[key] ?: Effort(thresholdEffort(workout)).also { efforts[key] = it }
                effort.bpm?.let { ThresholdEffort(workout.id, TrainingLoads.dateOf(workout, clock.zone), it) }
            }
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    /**
     * Heart rate anchors for any day: the athlete's values, completed with the estimated maximum and
     * threshold and the resting heart rate measured during sleep (daytime estimates run 10 to 20 bpm
     * higher and are not used).
     */
    val anchors: Flow<AnchorTimeline> = combine(
        physiology.observe(),
        nights.observeNights(),
        estimatedMax,
        thresholdEfforts,
        preferences.preferences.map { it.zoneBoundaries }.distinctUntilChanged(),
    ) { history, nightly, max, thresholds, boundaries ->
        AnchorTimeline(
            history,
            nightly.mapNotNull { night ->
                night.restingHr?.let { night.date to it }
            }.toMap(),
            max,
            thresholds,
            boundaries,
        )
    }

    private suspend fun thresholdEffort(workout: Workout): Double? {
        val streams = workouts.cleanStreams(workout)
        // Runs from before August 2026 have no speed stream; their GPS track stands in.
        val speed = if (streams.speed.size > 0) streams.speed else streams.route.speeds()
        return ThresholdHeartRateEstimate.effort(streams.heartRate, speed, workout.start.toEpochMilli(), workout.end.toEpochMilli())
    }

    /**
     * Load of every workout that counts, oldest first: duplicates, accidental starts and recordings
     * left running for days are left out.
     */
    fun observeWorkoutLoads(): Flow<List<WorkoutLoad>> = combine(
        workouts.observeAll(),
        anchors,
        preferences.preferences.map { it.strengthInLoad to it.sex.weighting }.distinctUntilChanged(),
        ratings.observe(),
    ) { all, timeline, (strength, weighting), rated -> LoadInputs(all, timeline, strength, weighting, rated) }
        .mapLatest { loads(it) }
        .flowOn(Dispatchers.Default)

    private class LoadInputs(
        val all: List<Workout>,
        val timeline: AnchorTimeline,
        val strength: Boolean,
        val weighting: BanisterWeighting,
        val efforts: Map<String, Int>,
    )

    /** The fitness-fatigue model from the first day with load up to today. */
    fun observeFitness(): Flow<List<FormDay>> = observeWorkoutLoads().map { loads ->
        FitnessModel.series(TrainingLoads.daily(loads), until = LocalDate.now(clock))
    }

    /**
     * Gym sessions are left out when the athlete chose so; a gym session they rated takes its stress score
     * from the rating ([EffortLoad]), since heart rate barely reflects lifting.
     */
    private suspend fun loads(inputs: LoadInputs): List<WorkoutLoad> {
        val counted = inputs.all.filter {
            TrainingLoads.counts(it) && Duration.between(it.start, it.end) >= TrainingSessions.MIN_DURATION &&
                (inputs.strength || it.sport != Sport.STRENGTH)
        }
        return TrainingSessions.removeDuplicates(counted).sortedBy { it.start }.map { workout ->
            val date = TrainingLoads.dateOf(workout, clock.zone)
            val anchors = inputs.timeline.at(date)
            val key = CacheKey(workout.id, workout.lastModified, anchors, inputs.weighting)
            val cached = cache[key] ?: score(workout, anchors, inputs.weighting).also { cache[key] = it }
            val millis = Duration.between(workout.start, workout.end).toMillis()
            val effort = inputs.efforts[workout.id]?.takeIf { workout.sport == Sport.STRENGTH }
            val scores = if (effort == null) {
                cached.scores
            } else {
                (cached.scores ?: LoadScores.ZERO).copy(stressScore = EffortLoad.stressScore(effort, millis))
            }
            WorkoutLoad(workout.id, date, millis, scores, cached.intensity, effort)
        }
    }

    private suspend fun score(workout: Workout, anchors: HeartRateAnchors?, weighting: BanisterWeighting): Cached {
        if (anchors == null) return Cached(null, null)
        val heartRate = StreamCleaning.heartRate(workouts.heartRate(workout.id))
        if (heartRate.isEmpty()) return Cached(null, null)
        return Cached(
            HeartRateLoad.of(heartRate, anchors, weighting),
            TimeInZones.compute(heartRate, Zones.of(ZoneModel.POLARIZED_THREE, anchors)),
        )
    }
}
