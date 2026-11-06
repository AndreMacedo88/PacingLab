package app.pacinglab.core.model

import java.time.Instant
import java.time.ZoneOffset

/**
 * One brief psychomotor vigilance test (PVT-B, Basner, Mollicone and Dinges 2011) taken in the app: three
 * minutes of tapping as soon as a counter starts. How the answers are scored is decided in core/analytics.
 *
 * @property start when the first wait began.
 * @property reactionMillis the response time of every stimulus in order, from 100 ms up; a stimulus left
 *   unanswered is [TIMEOUT_MILLIS].
 * @property falseStarts taps while no counter ran, and taps within 100 ms of a counter starting, which come
 *   too soon to answer it.
 */
data class ReactionTest(val start: Instant, val zoneOffset: ZoneOffset?, val reactionMillis: List<Int>, val falseStarts: Int) {
    companion object {
        /** A stimulus nobody answers ends after this long and counts as a lapse of this length. */
        const val TIMEOUT_MILLIS = 30_000

        /** Responses faster than this cannot be answers to the stimulus, so they count as false starts. */
        const val FASTEST_MILLIS = 100
    }
}
