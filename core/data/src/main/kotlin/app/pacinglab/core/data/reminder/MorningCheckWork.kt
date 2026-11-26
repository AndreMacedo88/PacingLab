package app.pacinglab.core.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pacinglab.core.data.repository.GoogleHealthRepository
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.data.sync.HealthSync
import app.pacinglab.core.data.sync.runAfterSync
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/**
 * The morning checks' alarm: inexact, so it needs no permission, and allowed while idle, so that it fires while
 * the phone lies still overnight. A set alarm is a pending intent; the receiver drops it when it fires.
 */
class PlatformMorningCheckAlarm @Inject constructor(@param:ApplicationContext private val context: Context) : MorningCheckAlarm {
    private val alarms: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override fun set(at: Instant) {
        alarms?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, create = true) ?: return)
    }

    override fun cancel() {
        pending(context, create = false)?.let { intent ->
            alarms?.cancel(intent)
            intent.cancel()
        }
    }

    override fun isSet(): Boolean = pending(context, create = false) != null

    internal companion object {
        fun pending(context: Context, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MorningCheckReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE,
        )
    }
}

/** Hands a fired check to WorkManager, expedited so that it runs while the phone is idle. */
class MorningCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlatformMorningCheckAlarm.pending(context, create = false)?.cancel()
        val request = OneTimeWorkRequestBuilder<MorningCheckWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK = "morning-check"
    }
}

/** One morning check; a full sync, like the periodic one, when last night has arrived. */
@HiltWorker
class MorningCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val check: MorningCheck,
    private val sync: HealthSync,
    private val googleHealth: GoogleHealthRepository,
    private val afterSync: Set<@JvmSuppressWildcards AfterSync>,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        check.check {
            sync.sync()
            googleHealth.sync()
            runAfterSync(afterSync)
        }
        return Result.success()
    }
}
