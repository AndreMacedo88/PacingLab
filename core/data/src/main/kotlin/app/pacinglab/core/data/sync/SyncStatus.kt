package app.pacinglab.core.data.sync

import java.time.Instant

/** What the sync engine is doing, for display. */
sealed interface SyncStatus {
    data object Idle : SyncStatus

    /**
     * First-time import of history, newest first.
     *
     * @property reached oldest time imported so far.
     * @property fraction share of the history already imported, 0 to 1.
     */
    data class Importing(val reached: Instant?, val fraction: Float) : SyncStatus

    /** Applying recent changes. */
    data object Updating : SyncStatus
}

/**
 * Result of one sync run.
 *
 * @property rateLimited Health Connect asked the app to slow down; the run stopped early and should be
 *   repeated after a pause. Progress is kept.
 * @property deferred the history import waits until the app is in the foreground.
 * @property errors human-readable problems; the run may still have made progress.
 */
data class SyncOutcome(
    val savedWorkouts: Int,
    val deletedWorkouts: Int,
    val importComplete: Boolean,
    val noAccess: Boolean = false,
    val rateLimited: Boolean = false,
    val deferred: Boolean = false,
    val errors: List<String> = emptyList(),
) {
    val succeeded: Boolean get() = !noAccess && !rateLimited && errors.isEmpty()
}

/** Why a sync the athlete asked for did not bring everything up to date. */
enum class SyncProblem {
    /** PacingLab may not read workouts from Health Connect, or Health Connect is missing. */
    NO_ACCESS,

    /** Health Connect asked for a pause; the sync carries on after it. */
    RATE_LIMITED,

    /** Some data could not be read from Health Connect. */
    HEALTH_CONNECT_FAILED,

    /** The Google Health API wants the athlete to sign in again. */
    GOOGLE_HEALTH_SIGN_IN,

    /** The Google Health API could not be reached, usually for want of a connection. */
    GOOGLE_HEALTH_UNREACHABLE,
}

/** Persistent sync facts shown in the UI. */
data class SyncInfo(val lastSuccess: Instant?, val importComplete: Boolean, val importedSince: Instant?)
