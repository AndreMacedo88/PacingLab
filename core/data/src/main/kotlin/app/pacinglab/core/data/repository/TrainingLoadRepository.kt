package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.FormDay
import app.pacinglab.core.analytics.load.HeartRateLoad
import app.pacinglab.core.analytics.load.LoadScores
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.load.WorkoutLoad
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.analytics.zones.TimeInZones
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.database.store.PhysiologyStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.ZoneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val clock: Clock,
) {
    private class Cached(val scores: LoadScores?, val intensity: ZoneDistribution?)

    private data class CacheKey(val workoutId: String, val lastModified: Instant, val anchors: HeartRateAnchors?)

    /** Scores survive recomposition and syncs; a workout is only recalculated when it or its anchors change. */
    private val cache = ConcurrentHashMap<CacheKey, Cached>()

    /**
     * Heart rate anchors for any day: the athlete's values, completed with the resting heart rate measured
     * during sleep (daytime estimates run 10 to 20 bpm higher and are not used).
     */
    val anchors: Flow<AnchorTimeline> = combine(physiology.observe(), nights.observeNights()) { history, nightly ->
        AnchorTimeline(history, nightly.mapNotNull { night -> night.restingHr?.let { night.date to it } }.toMap())
    }

    /**
     * Load of every workout that counts, oldest first: duplicates, accidental starts and recordings
     * left running for days are left out.
     */
    fun observeWorkoutLoads(): Flow<List<WorkoutLoad>> = combine(workouts.observeAll(), anchors) { all, timeline -> all to timeline }
        .mapLatest { (all, timeline) -> loads(all, timeline) }
        .flowOn(Dispatchers.Default)

    /** The fitness-fatigue model from the first day with load up to today. */
    fun observeFitness(): Flow<List<FormDay>> = observeWorkoutLoads().map { loads ->
        FitnessModel.series(TrainingLoads.daily(loads), until = LocalDate.now(clock))
    }

    private suspend fun loads(all: List<Workout>, timeline: AnchorTimeline): List<WorkoutLoad> {
        val counted = all.filter { TrainingLoads.counts(it) && Duration.between(it.start, it.end) >= TrainingSessions.MIN_DURATION }
        return TrainingSessions.removeDuplicates(counted).sortedBy { it.start }.map { workout ->
            val date = TrainingLoads.dateOf(workout, clock.zone)
            val anchors = timeline.at(date)
            val key = CacheKey(workout.id, workout.lastModified, anchors)
            val cached = cache[key] ?: score(workout, anchors).also { cache[key] = it }
            WorkoutLoad(workout.id, date, Duration.between(workout.start, workout.end).toMillis(), cached.scores, cached.intensity)
        }
    }

    private suspend fun score(workout: Workout, anchors: HeartRateAnchors?): Cached {
        if (anchors == null) return Cached(null, null)
        val heartRate = workouts.heartRate(workout.id)
        if (heartRate.isEmpty()) return Cached(null, null)
        return Cached(HeartRateLoad.of(heartRate, anchors), TimeInZones.compute(heartRate, Zones.of(ZoneModel.POLARIZED_THREE, anchors)))
    }
}
