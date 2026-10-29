package app.pacinglab.core.data.sync

/**
 * Work that follows every sync, in the app or in the background: the home-screen widget, the morning
 * reminder. Modules add theirs to the set Hilt collects; a failing one never stops the others or the sync.
 */
fun interface AfterSync {
    suspend fun run()
}
