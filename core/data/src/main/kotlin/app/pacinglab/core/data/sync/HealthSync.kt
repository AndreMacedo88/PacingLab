package app.pacinglab.core.data.sync

import app.pacinglab.core.analytics.workout.RouteExpectation
import app.pacinglab.core.analytics.workout.SportClassifier
import app.pacinglab.core.analytics.workout.WorkoutSummaries
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.SyncStateStore
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SourceWorkout
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutStreams
import app.pacinglab.core.model.source.ChangeBatch
import app.pacinglab.core.model.source.ChangeChannel
import app.pacinglab.core.model.source.HealthDataSource
import app.pacinglab.core.model.source.RateLimitedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the local database in step with the health data source.
 *
 * The first run imports all history, newest first, in windows of [IMPORT_WINDOW]. Progress is stored
 * after every window, so an interrupted import resumes where it stopped. The import only runs while
 * the app is visible: Health Connect allows far fewer reads in the background and withholds routes
 * there. Change tokens are taken before the import starts, so nothing written meanwhile is missed.
 * Later runs apply changes, one channel (record type) at a time; a failing channel does not stop the
 * others. When Health Connect rate-limits the app, the run stops, keeps its progress and reports
 * [SyncOutcome.rateLimited] so the caller can try again after a pause.
 */
@Singleton
class HealthSync @Inject constructor(
    private val source: HealthDataSource,
    private val workouts: WorkoutStore,
    private val measurements: MeasurementStore,
    private val state: SyncStateStore,
    private val visibility: AppVisibility,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    private val _lastOutcome = MutableStateFlow<SyncOutcome?>(null)

    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Result of the most recent run in this process, null before the first one. */
    val lastOutcome: StateFlow<SyncOutcome?> = _lastOutcome.asStateFlow()

    val info: Flow<SyncInfo> = state.observe().map { values ->
        SyncInfo(
            lastSuccess = values[LAST_SUCCESS]?.toLongOrNull()?.let(Instant::ofEpochMilli),
            importComplete = values[IMPORT_DONE] == "true",
            importedSince = values[IMPORT_CURSOR]?.toLongOrNull()?.let(Instant::ofEpochMilli),
        )
    }

    /** Runs one sync. Concurrent calls wait for the running one and then run again. */
    suspend fun sync(): SyncOutcome = mutex.withLock {
        val tally = Tally()
        try {
            run(tally)
        } catch (e: RateLimitedException) {
            tally.rateLimited = true
        } finally {
            _status.value = SyncStatus.Idle
        }
        tally.outcome().also { _lastOutcome.value = it }
    }

    private suspend fun run(tally: Tally) {
        val channels = attempt(tally, "permissions") { source.availableChannels() } ?: return
        if (ChangeChannel.WORKOUTS !in channels) {
            tally.noAccess = true
            return
        }
        val foreground = visibility.isForeground
        restartImportIfOutdated()

        val newChannels = channels.filter { state.get(tokenKey(it)) == null }
        for (channel in newChannels) {
            attempt(tally, "start tracking $channel") { state.put(tokenKey(channel), source.changesToken(channel)) }
        }

        var importDone = state.get(IMPORT_DONE) == "true"
        if (!importDone) {
            if (!foreground) {
                tally.deferred = true
                return
            }
            importDone = importHistory(channels, tally)
            tally.importComplete = importDone
            if (!importDone) return
        } else {
            tally.importComplete = true
            // Daily values granted after the import still need their history.
            val late = newChannels.filter { it.measurementKind != null || it == ChangeChannel.SLEEP }
            if (late.isNotEmpty() && newChannels.size < channels.size) {
                attempt(tally, "history for new data types") { importDailyHistory(late.toSet()) }
            }
        }

        _status.value = SyncStatus.Updating
        for (channel in channels) {
            attempt(tally, "changes for $channel") { applyChanges(channel, channels, tally, foreground) }
        }
        if (foreground) attempt(tally, "pending routes") { fetchPendingRoutes(tally) }
        if (tally.errors.isEmpty()) state.put(LAST_SUCCESS, clock.millis().toString())
    }

    /** Starts the import over when stored workouts were produced by an older version of this sync. */
    private suspend fun restartImportIfOutdated() {
        if (state.get(SYNC_VERSION) == CURRENT_SYNC_VERSION) return
        state.remove(IMPORT_START)
        state.remove(IMPORT_CURSOR)
        state.remove(IMPORT_DONE)
        state.put(SYNC_VERSION, CURRENT_SYNC_VERSION)
    }

    /** Returns true once the whole history is imported. */
    private suspend fun importHistory(channels: Set<ChangeChannel>, tally: Tally): Boolean {
        val newest = state.get(IMPORT_START)?.toLong() ?: clock.millis().also { state.put(IMPORT_START, it.toString()) }
        val earliest = attempt(tally, "find oldest workout") { source.earliestWorkoutTime() } ?: run {
            if (tally.errors.isNotEmpty()) return false
            state.put(IMPORT_DONE, "true")
            return true
        }
        var cursor = state.get(IMPORT_CURSOR)?.toLong() ?: newest
        val span = (newest - earliest.toEpochMilli()).coerceAtLeast(1)
        while (cursor > earliest.toEpochMilli()) {
            if (!visibility.isForeground) {
                tally.deferred = true
                return false
            }
            val from = maxOf(earliest.toEpochMilli(), cursor - IMPORT_WINDOW.toMillis())
            _status.value = SyncStatus.Importing(Instant.ofEpochMilli(cursor), (newest - cursor).toFloat() / span)
            val done = attempt(tally, "import ${Instant.ofEpochMilli(from)}") {
                importWindow(Instant.ofEpochMilli(from), Instant.ofEpochMilli(cursor), channels, tally)
            }
            if (done == null) return false
            cursor = from
            state.put(IMPORT_CURSOR, cursor.toString())
        }
        state.put(IMPORT_DONE, "true")
        return true
    }

    private suspend fun importWindow(from: Instant, to: Instant, channels: Set<ChangeChannel>, tally: Tally) {
        saveWorkouts(source.readWorkouts(from, to), tally, foreground = true)
        importDaily(channels, from, to)
    }

    private suspend fun importDailyHistory(channels: Set<ChangeChannel>) {
        val now = clock.instant()
        var to = now
        val oldest = now.minus(DAILY_HISTORY)
        while (to > oldest) {
            val from = maxOf(oldest, to.minus(IMPORT_WINDOW))
            importDaily(channels, from, to)
            to = from
        }
    }

    private suspend fun importDaily(channels: Set<ChangeChannel>, from: Instant, to: Instant) {
        for (channel in channels) {
            val kind = channel.measurementKind ?: continue
            measurements.saveMeasurements(source.readMeasurements(kind, from, to))
        }
        if (ChangeChannel.SLEEP in channels) saveSleep(source.readSleep(from, to), channels)
    }

    /** Saves sleep sessions with the heart rate recorded during them, when heart rate may be read. */
    private suspend fun saveSleep(sessions: List<SleepSession>, channels: Set<ChangeChannel>) {
        if (sessions.isEmpty()) return
        val withHeartRate = if (ChangeChannel.HEART_RATE in channels) {
            sessions.map { it.copy(heartRate = source.readHeartRate(it.start, it.end, it.origin)) }
        } else {
            sessions
        }
        measurements.saveSleep(withHeartRate)
    }

    /** Heart rate written after the sleep it belongs to (the watch syncs in batches) is picked up here. */
    private suspend fun refreshSleepHeartRate(spans: List<ClosedRange<Instant>>) {
        if (spans.isEmpty()) return
        val candidates = measurements.sleepOverlapping(spans.minOf { it.start }, spans.maxOf { it.endInclusive })
        for (session in candidates) {
            if (spans.none { span -> span.start <= session.end && span.endInclusive >= session.start }) continue
            measurements.saveSleepHeartRate(session.sourceRecordId, source.readHeartRate(session.start, session.end, session.origin))
        }
    }

    private suspend fun applyChanges(channel: ChangeChannel, channels: Set<ChangeChannel>, tally: Tally, foreground: Boolean) {
        var token = state.get(tokenKey(channel)) ?: return
        do {
            val batch = source.readChanges(channel, token)
            if (batch.tokenExpired) {
                recoverExpired(channel, channels, tally, foreground)
                return
            }
            apply(channel, batch, channels, tally, foreground)
            token = batch.nextToken
            state.put(tokenKey(channel), token)
        } while (batch.hasMore)
    }

    private suspend fun apply(channel: ChangeChannel, batch: ChangeBatch, channels: Set<ChangeChannel>, tally: Tally, foreground: Boolean) {
        when (channel) {
            ChangeChannel.WORKOUTS -> {
                saveWorkouts(batch.workouts, tally, foreground)
                deleteWorkouts(batch.deletedRecordIds, tally)
            }

            ChangeChannel.HEART_RATE -> {
                refreshStreams(batch.affectedSpans, tally, foreground)
                if (ChangeChannel.SLEEP in channels) refreshSleepHeartRate(batch.affectedSpans)
            }

            ChangeChannel.SPEED, ChangeChannel.DISTANCE, ChangeChannel.STEPS -> refreshStreams(batch.affectedSpans, tally, foreground)

            ChangeChannel.SLEEP -> {
                saveSleep(batch.sleep, channels)
                measurements.delete(batch.deletedRecordIds)
            }

            else -> {
                measurements.saveMeasurements(batch.measurements)
                measurements.delete(batch.deletedRecordIds)
            }
        }
    }

    /** Workouts whose heart rate, speed or distance changed afterwards get their streams read again. */
    private suspend fun refreshStreams(spans: List<ClosedRange<Instant>>, tally: Tally, foreground: Boolean) {
        if (spans.isEmpty()) return
        val candidates = workouts.overlapping(spans.minOf { it.start }, spans.maxOf { it.endInclusive })
        val affected = candidates.filter { workout ->
            spans.any { span -> span.start <= workout.end && span.endInclusive >= workout.start }
        }
        // The stored route is still valid; only the other streams changed.
        saveWorkouts(affected.map { it.toSourceWorkout(route = null) }, tally, foreground)
    }

    /** A token unused for too long expires; re-read the recent past and start tracking afresh. */
    private suspend fun recoverExpired(channel: ChangeChannel, channels: Set<ChangeChannel>, tally: Tally, foreground: Boolean) {
        val to = clock.instant()
        val from = to.minus(EXPIRED_TOKEN_REREAD)
        val token = source.changesToken(channel)
        when (channel) {
            ChangeChannel.WORKOUTS -> {
                val fresh = source.readWorkouts(from, to)
                val freshIds = fresh.mapTo(mutableSetOf()) { it.sourceRecordId }
                val vanished = workouts.overlapping(from, to)
                    .filter { it.source == DataSource.HEALTH_CONNECT && it.start >= from && it.sourceRecordId !in freshIds }
                deleteWorkouts(vanished.map { it.sourceRecordId }, tally)
                saveWorkouts(fresh, tally, foreground)
            }

            ChangeChannel.HEART_RATE, ChangeChannel.SPEED, ChangeChannel.DISTANCE, ChangeChannel.STEPS -> {
                saveWorkouts(workouts.overlapping(from, to).map { it.toSourceWorkout(route = null) }, tally, foreground)
                if (channel == ChangeChannel.HEART_RATE && ChangeChannel.SLEEP in channels) refreshSleepHeartRate(listOf(from..to))
            }

            else -> importDaily(setOf(channel).intersect(channels), from, to)
        }
        state.put(tokenKey(channel), token)
    }

    /** Reads routes that could not be read before (in the background), in bulk per time window. */
    private suspend fun fetchPendingRoutes(tally: Tally) {
        val pending = workouts.routePending().take(MAX_PENDING_ROUTES_PER_RUN)
        if (pending.isEmpty()) return
        val windows = pending.groupBy { it.start.toEpochMilli() / IMPORT_WINDOW.toMillis() }.values
        for (window in windows) {
            val from = window.minOf { it.start }
            val to = window.maxOf { it.start }.plusMillis(1)
            val fresh = source.readWorkouts(from, to).associateBy { it.sourceRecordId }
            for (workout in window) {
                val route = fresh[workout.sourceRecordId]?.route ?: continue
                val streams = workouts.streams(workout.id).copy(route = route)
                val sourceWorkout = workout.toSourceWorkout(route)
                workouts.save(
                    workout.copy(routePending = false, summary = WorkoutSummaries.summarize(sourceWorkout, streams, workout.sport)),
                    streams,
                )
                tally.saved++
            }
        }
    }

    private suspend fun saveWorkouts(sourceWorkouts: List<SourceWorkout>, tally: Tally, foreground: Boolean) {
        if (sourceWorkouts.isEmpty()) return
        val streams = source.readStreams(sourceWorkouts)
        for (sourceWorkout in sourceWorkouts) {
            val read = streams[sourceWorkout.sourceRecordId] ?: WorkoutStreams.EMPTY
            val resolved = resolveRoute(sourceWorkout, foreground)
            val workoutStreams = read.copy(route = resolved.route)
            val sport = SportClassifier.classify(
                sourceWorkout.activity,
                workoutStreams.speed,
                workoutStreams.reportedDistanceMeters,
                Duration.between(sourceWorkout.start, sourceWorkout.end).toMillis(),
            )
            workouts.save(sourceWorkout.toWorkout(sport, workoutStreams, resolved.pending), workoutStreams)
            tally.saved++
        }
    }

    /**
     * Decides which route to store. A route the source could not deliver now (null, or nothing at all
     * while in the background, where Health Connect withholds routes) never replaces one stored
     * earlier; if none is stored, the workout is marked for a later attempt.
     */
    private suspend fun resolveRoute(workout: SourceWorkout, foreground: Boolean): ResolvedRoute {
        val delivered = workout.route
        val trustworthy = delivered != null && (foreground || !delivered.isEmpty() || !RouteExpectation.possible(workout.activity))
        if (trustworthy) return ResolvedRoute(delivered, pending = false)
        val id = workoutId(workout.sourceRecordId)
        val stored = workouts.get(id)
        return when {
            stored == null -> ResolvedRoute(Route.EMPTY, pending = RouteExpectation.possible(workout.activity))
            stored.summary.hasRoute -> ResolvedRoute(workouts.streams(id).route, pending = false)
            else -> ResolvedRoute(Route.EMPTY, pending = stored.routePending || RouteExpectation.possible(workout.activity))
        }
    }

    private suspend fun deleteWorkouts(recordIds: List<String>, tally: Tally) {
        if (recordIds.isEmpty()) return
        tally.deleted += workouts.deleteBySourceIds(DataSource.HEALTH_CONNECT, recordIds)
    }

    private fun SourceWorkout.toWorkout(sport: Sport, streams: WorkoutStreams, routePending: Boolean) = Workout(
        id = workoutId(sourceRecordId),
        source = DataSource.HEALTH_CONNECT,
        sourceRecordId = sourceRecordId,
        origin = origin,
        device = device,
        activity = activity,
        title = title,
        start = start,
        end = end,
        zoneOffset = zoneOffset,
        lastModified = lastModified,
        sport = sport,
        summary = WorkoutSummaries.summarize(this, streams, sport),
        laps = laps,
        segments = segments,
        routePending = routePending,
        recordingMethod = recordingMethod,
    )

    private fun Workout.toSourceWorkout(route: Route?) = SourceWorkout(
        sourceRecordId = sourceRecordId,
        origin = origin,
        device = device,
        activity = activity,
        title = title,
        start = start,
        end = end,
        zoneOffset = zoneOffset,
        lastModified = lastModified,
        laps = laps,
        segments = segments,
        route = route,
        recordingMethod = recordingMethod,
    )

    private suspend inline fun <T> attempt(tally: Tally, what: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: RateLimitedException) {
        throw e
    } catch (e: Exception) {
        tally.errors += "$what: ${e::class.simpleName}: ${e.message.orEmpty()}"
        null
    }

    private data class ResolvedRoute(val route: Route, val pending: Boolean)

    private class Tally {
        var saved = 0
        var deleted = 0
        var importComplete = false
        var noAccess = false
        var rateLimited = false
        var deferred = false
        val errors = mutableListOf<String>()

        fun outcome() = SyncOutcome(saved, deleted, importComplete, noAccess, rateLimited, deferred, errors.toList())
    }

    companion object {
        val IMPORT_WINDOW: Duration = Duration.ofDays(30)
        val EXPIRED_TOKEN_REREAD: Duration = Duration.ofDays(30)

        /** How far back to read daily values for data types granted after the first import. */
        val DAILY_HISTORY: Duration = Duration.ofDays(400)

        /** Bounds the work one run spends on catching up routes. */
        const val MAX_PENDING_ROUTES_PER_RUN = 400

        /**
         * Bump when stored workouts must be rebuilt from the source, for example after changing how
         * streams are read. The next foreground sync then re-imports the whole history.
         * 3: workouts carry their recording method.
         * 4: sleep sessions carry the heart rate recorded during them.
         * 5: sprint sessions in interval mode are classified as runs.
         * 6: workouts carry their step counts (cadence and stride length).
         */
        const val CURRENT_SYNC_VERSION = "6"

        fun workoutId(sourceRecordId: String) = "hc:$sourceRecordId"

        private fun tokenKey(channel: ChangeChannel) = "token.${channel.name}"

        private const val SYNC_VERSION = "sync.version"
        private const val IMPORT_START = "import.start"
        private const val IMPORT_CURSOR = "import.cursor"
        private const val IMPORT_DONE = "import.done"
        private const val LAST_SUCCESS = "last.success"
    }
}
