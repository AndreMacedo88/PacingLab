package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.session.SessionAnalyzer
import app.pacinglab.core.analytics.session.SessionClassifier
import app.pacinglab.core.analytics.session.SessionPart
import app.pacinglab.core.analytics.workout.TrainingSession
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What each running session was for ([SessionClassifier]), worked out once per version of its workouts
 * and heart rate values, because it needs the streams of every part.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionKindRepository @Inject constructor(
    private val workouts: WorkoutRepository,
    trainingLoad: TrainingLoadRepository,
    private val clock: Clock,
) {
    private data class Key(val parts: List<Pair<String, Instant>>, val anchors: HeartRateAnchors?)

    private val cache = ConcurrentHashMap<Key, SessionKind>()

    private val kinds: Flow<Map<String, SessionKind>> = combine(workouts.observeSessions(), trainingLoad.anchors) { sessions, timeline ->
        sessions to timeline
    }
        .mapLatest { (sessions, timeline) -> sessions.filter { it.sport == Sport.RUN }.associate { it.id to kindOf(it, timeline) } }
        .flowOn(Dispatchers.Default)

    /** The kind of every running session by session id; empty until they are worked out. */
    fun observeKinds(): Flow<Map<String, SessionKind>> = kinds.onStart { emit(emptyMap()) }

    /** The kind of a running [session] with the heart rate values of its day. */
    suspend fun kindOf(session: TrainingSession, timeline: AnchorTimeline): SessionKind {
        val anchors = timeline.at(TrainingLoads.dateOf(session.workouts.first(), clock.zone))
        val key = Key(session.workouts.map { it.id to it.lastModified }, anchors)
        return cache[key] ?: classify(session, anchors).also { cache[key] = it }
    }

    private suspend fun classify(session: TrainingSession, anchors: HeartRateAnchors?): SessionKind {
        val parts = session.workouts.map { SessionPart(it, workouts.streams(it.id)) }
        return SessionClassifier.classify(
            analysis = SessionAnalyzer.analyze(parts),
            heartRate = HeartRateSeries.joined(parts.map { it.streams.heartRate }),
            zones = anchors?.let { Zones.of(ZoneModel.LTHR_FIVE, it) },
            movingMillis = parts.sumOf { it.workout.summary.durationMillis },
        )
    }
}
