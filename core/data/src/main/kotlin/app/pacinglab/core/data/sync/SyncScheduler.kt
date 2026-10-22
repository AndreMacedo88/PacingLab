package app.pacinglab.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pacinglab.core.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    private val visibility: AppVisibility,
) {
    private var running: Job? = null

    /**
     * True while a sync of recent changes runs, for pull-to-refresh indicators. The first import shows
     * its own progress and does not count.
     */
    val updating: Flow<Boolean> = sync.status.map { it == SyncStatus.Updating }.distinctUntilChanged()

    /**
     * Syncs now unless a sync started from here is still running. When Health Connect rate-limits the
     * app, waits and continues, for as long as the app stays visible.
     */
    fun syncNow() {
        if (running?.isActive == true) return
        running = scope.launch {
            repeat(MAX_RATE_LIMITED_RUNS) {
                val outcome = sync.sync()
                if (!outcome.rateLimited || !visibility.isForeground) return@launch
                delay(RATE_LIMIT_PAUSE.toMillis())
            }
        }
    }

    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PERIODIC_WORK = "health-sync"
        val PERIOD: Duration = Duration.ofHours(1)
        val RATE_LIMIT_PAUSE: Duration = Duration.ofMinutes(1)
        const val MAX_RATE_LIMITED_RUNS = 30
    }
}
