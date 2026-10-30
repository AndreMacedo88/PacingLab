package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.load.AnchorTimeline
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.session.HeartRateRecovery
import app.pacinglab.core.analytics.session.RepRecovery
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A running session's heart rate recovery after reps, for the trend. */
data class SessionRecoveryPoint(val date: LocalDate, val sessionId: String, val recovery: RepRecovery)

/**
 * What each running session was for ([SessionClassifier]) and how fast heart rate fell after its reps
 * ([HeartRateRecovery.ofSession]), worked out once per version of its workouts and heart rate values,
 * because both need the streams of every part.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SessionKindRepository @Inject constructor(
    private val workouts: WorkoutRepository,
    trainingLoad: TrainingLoadRepository,
    private val clock: Clock,
) {
    private data class Key(val parts: List<Pair<String, Instant>>, val anchors: HeartRateAnchors?)

    private class Facts(val kind: SessionKind, val recovery: RepRecovery?)

    private val cache = ConcurrentHashMap<Key, Facts>()

    private val facts: Flow<Map<TrainingSession, Facts>> = combine(workouts.observeSessions(), trainingLoad.anchors) { sessions, timeline ->
        sessions to timeline
    }
        .mapLatest { (sessions, timeline) -> sessions.filter { it.sport == Sport.RUN }.associateWith { factsOf(it, timeline) } }
        .flowOn(Dispatchers.Default)

    /** The kind of every running session by session id; empty until they are worked out. */
    fun observeKinds(): Flow<Map<String, SessionKind>> = facts.map { all -> all.entries.associate { (session, f) -> session.id to f.kind } }

    /** Each running session's kind under the id of every workout in it. */
    fun observeKindsByWorkout(): Flow<Map<String, SessionKind>> = facts.map { all ->
        all.entries.flatMap { (session, f) -> session.workouts.map { it.id to f.kind } }.toMap()
    }
        .onStart { emit(emptyMap()) }

    /** Heart rate recovery after reps of every running session that has it, oldest first. */
    fun observeRecoveries(): Flow<List<SessionRecoveryPoint>> = facts.map { all ->
        all.mapNotNull { (session, f) ->
            f.recovery?.let { SessionRecoveryPoint(TrainingLoads.dateOf(session.workouts.first(), clock.zone), session.id, it) }
        }.sortedBy { it.date }
    }

    /** The kind of a running [session] with the heart rate values of its day. */
    suspend fun kindOf(session: TrainingSession, timeline: AnchorTimeline): SessionKind = factsOf(session, timeline).kind

    private suspend fun factsOf(session: TrainingSession, timeline: AnchorTimeline): Facts {
        val anchors = timeline.at(TrainingLoads.dateOf(session.workouts.first(), clock.zone))
        val key = Key(session.workouts.map { it.id to it.lastModified }, anchors)
        return cache[key] ?: analyze(session, anchors).also { cache[key] = it }
    }

    private suspend fun analyze(session: TrainingSession, anchors: HeartRateAnchors?): Facts {
        val parts = session.workouts.map { SessionPart(it, workouts.streams(it)) }
        val analysis = SessionAnalyzer.analyze(parts)
        val kind = SessionClassifier.classify(
            analysis = analysis,
            heartRate = HeartRateSeries.joined(parts.map { it.streams.heartRate }),
            zones = anchors?.let { Zones.of(ZoneModel.LTHR_FIVE, it) },
            movingMillis = parts.sumOf { it.workout.summary.durationMillis },
        )
        return Facts(kind, anchors?.let { HeartRateRecovery.ofSession(analysis, it.maxHr) })
    }
}
