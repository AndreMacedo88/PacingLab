package app.pacinglab.core.model.source

import app.pacinglab.core.model.DailyVitals
import java.time.LocalDate

/** A secondary source of the watch app's nightly values, such as the Google Health API. */
interface VitalsSource {
    /**
     * Every day's values from [since] on, one per date. Throws [SourceAuthorizationException] when the
     * athlete has to sign in again and an IOException without a connection.
     */
    suspend fun readDailyVitals(since: LocalDate): List<DailyVitals>
}

/** The source needs the athlete to sign in or grant access again. */
class SourceAuthorizationException(message: String, cause: Throwable? = null) : Exception(message, cause)
