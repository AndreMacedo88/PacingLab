package app.pacinglab

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.pacinglab.core.data.di.ApplicationScope
import app.pacinglab.core.data.reminder.MorningCheck
import app.pacinglab.core.data.sync.ProcessAppVisibility
import app.pacinglab.core.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PacingLabApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    @Inject lateinit var visibility: ProcessAppVisibility

    @Inject lateinit var morningCheck: MorningCheck

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        visibility.start()
        syncScheduler.schedulePeriodic()
        // Alarms do not survive a restart or an update; the morning checks are set again here.
        scope.launch { morningCheck.run() }
    }
}
