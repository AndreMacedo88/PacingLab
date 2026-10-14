package app.pacinglab.core.data.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the app is visible. Health Connect allows far more reads, and route reads, only then. */
interface AppVisibility {
    val isForeground: Boolean
}

@Singleton
class ProcessAppVisibility @Inject constructor() :
    AppVisibility,
    DefaultLifecycleObserver {
    @Volatile
    override var isForeground: Boolean = false
        private set

    /** Must be called once on the main thread, from Application.onCreate. */
    fun start() = ProcessLifecycleOwner.get().lifecycle.addObserver(this)

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
    }
}
