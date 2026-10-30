package app.pacinglab.core.data.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pacinglab.core.data.di.ApplicationScope
import app.pacinglab.core.data.repository.GoogleHealthProblem
import app.pacinglab.core.data.repository.GoogleHealthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts syncs. While the app is open, syncs run directly in the app process, where Health Connect
 * allows the most reads (the first import is large). In the background a periodic worker keeps data
 * fresh.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val sync: HealthSync,
    private val googleHealth: GoogleHealthRepository,
    private val visibility: AppVisibility,
    private val afterSync: Set<@JvmSuppressWildcards AfterSync>,
) {
    private var running: Job? = null
    private val pulled = MutableStateFlow(false)
    private val _problems = MutableSharedFlow<SyncProblem>(extraBufferCapacity = 1)

    /**
     * True while a sync of recent changes runs, for pull-to-refresh indicators. The first import shows
     * its own progress and does not count.
     */
    val updating: Flow<Boolean> = sync.status.map { it == SyncStatus.Updating }.distinctUntilChanged()

    /**
     * True while a sync the athlete asked for runs (at least [MIN_REFRESH_MILLIS], so the indicator is
     * seen to turn), or while any sync applies changes.
     */
    val refreshing: Flow<Boolean> = combine(pulled, updating) { asked, applying -> asked || applying }.distinctUntilChanged()

    /** What went wrong in a sync the athlete asked for, once per [refresh]; nothing when all went well. */
    val problems: SharedFlow<SyncProblem> = _problems.asSharedFlow()

    /**
     * A sync the athlete asked for, by pulling a screen down: both sources are read, the indicator turns
     * for at least [MIN_REFRESH_MILLIS] and a problem is reported through [problems]. When Health Connect
     * asks for a pause, the indicator stops and [syncNow] carries on after it.
     */
    fun refresh() {
        if (pulled.value) return
        pulled.value = true
        scope.launch {
            val minimum = launch { delay(MIN_REFRESH_MILLIS) }
            val outcome = sync.sync()
            val google = googleHealth.sync()
            minimum.join()
            problemOf(outcome, google)?.let { _problems.emit(it) }
            pulled.value = false
            // The widget and the weather lookups follow once the indicator has stopped.
            runAfterSync(afterSync)
            if (outcome.rateLimited) syncNow()
        }
    }

    /**
     * Syncs now unless a sync started from here is still running. When Health Connect rate-limits the
     * app, waits and continues, for as long as the app stays visible. The Google Health API, when
     * connected, is read after Health Connect.
     */
    fun syncNow() {
        if (running?.isActive == true) return
        running = scope.launch {
            syncHealthConnect()
            googleHealth.sync()
            runAfterSync(afterSync)
        }
    }

    private suspend fun syncHealthConnect() {
        repeat(MAX_RATE_LIMITED_RUNS) {
            val outcome = sync.sync()
            if (!outcome.rateLimited || !visibility.isForeground) return
            delay(RATE_LIMIT_PAUSE.toMillis())
        }
    }

    private fun problemOf(outcome: SyncOutcome, google: GoogleHealthProblem?): SyncProblem? = when {
        outcome.noAccess -> SyncProblem.NO_ACCESS
        outcome.rateLimited -> SyncProblem.RATE_LIMITED
        outcome.errors.isNotEmpty() -> SyncProblem.HEALTH_CONNECT_FAILED
        google == GoogleHealthProblem.SIGN_IN_NEEDED -> SyncProblem.GOOGLE_HEALTH_SIGN_IN
        google == GoogleHealthProblem.UNREACHABLE -> SyncProblem.GOOGLE_HEALTH_UNREACHABLE
        else -> null
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        /** How long the indicator turns at least after a pull, so that the athlete sees the sync start. */
        const val MIN_REFRESH_MILLIS = 1_000L
        private const val PERIODIC_WORK = "health-sync"
        private val PERIOD: Duration = Duration.ofHours(1)
        private val RATE_LIMIT_PAUSE: Duration = Duration.ofMinutes(1)
        private const val MAX_RATE_LIMITED_RUNS = 30
    }
}

/** Runs every [AfterSync]; one that fails is skipped, so the others and the sync carry on. */
internal suspend fun runAfterSync(tasks: Set<AfterSync>) {
    for (task in tasks) {
        try {
            task.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PacingLab", "After-sync work failed", e)
        }
    }
}
