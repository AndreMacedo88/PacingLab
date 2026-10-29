package app.pacinglab.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pacinglab.core.data.repository.GoogleHealthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Periodic background sync. Failed runs are retried with WorkManager's backoff. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: HealthSync,
    private val googleHealth: GoogleHealthRepository,
    private val afterSync: Set<@JvmSuppressWildcards AfterSync>,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = sync.sync()
        googleHealth.sync()
        runAfterSync(afterSync)
        return when {
            outcome.noAccess || outcome.deferred -> Result.success()
            outcome.rateLimited -> Result.retry()
            outcome.errors.isEmpty() -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
