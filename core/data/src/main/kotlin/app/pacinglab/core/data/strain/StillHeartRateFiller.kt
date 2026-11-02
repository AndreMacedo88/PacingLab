package app.pacinglab.core.data.strain

import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.data.sync.AppVisibility
import app.pacinglab.core.database.store.StillHeartRateStore
import app.pacinglab.core.model.StillHeartRate
import app.pacinglab.core.model.source.RateLimitedException
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * After each sync in the app, summarises the still heart rate of past days that have none yet, newest
 * first and as far back as [BACKFILL_DAYS], and again the last [RECHECK_DAYS] days, whose heart rate the
 * watch's app may still be passing on. Only in the foreground, where Health Connect allows the reads; a
 * rate limit ends the round and the next sync carries on.
 */
class StillHeartRateFiller @Inject constructor(
    private val reader: StillHeartRateReader,
    private val store: StillHeartRateStore,
    private val visibility: AppVisibility,
    private val clock: Clock,
) : AfterSync {
    override suspend fun run() {
        if (!visibility.isForeground) return
        val today = LocalDate.now(clock)
        val done = store.days()
        val wanted = (1..BACKFILL_DAYS).map { today.minusDays(it) }.filter { it !in done || it >= today.minusDays(RECHECK_DAYS) }
        val read = mutableListOf<StillHeartRate>()
        for (date in wanted) {
            try {
                read += reader.read(date)
            } catch (_: RateLimitedException) {
                break
            }
            if (read.size >= SAVE_EVERY) {
                store.save(read)
                read.clear()
            }
        }
        store.save(read)
    }

    companion object {
        const val BACKFILL_DAYS = 60L
        const val RECHECK_DAYS = 3L
        private const val SAVE_EVERY = 10
    }
}
