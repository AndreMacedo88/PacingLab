package app.pacinglab.core.data.home

import app.pacinglab.core.data.sync.AfterSync
import javax.inject.Inject

/** After each sync, in the app or in the background, reads the bedroom and sends PacingLab's values. */
class HomeAssistantSync @Inject constructor(private val repository: HomeAssistantRepository) : AfterSync {
    override suspend fun run() {
        repository.sync()
    }
}
