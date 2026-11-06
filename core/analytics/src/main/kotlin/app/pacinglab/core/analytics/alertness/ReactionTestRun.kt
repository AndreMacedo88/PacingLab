package app.pacinglab.core.analytics.alertness

import app.pacinglab.core.model.ReactionTest
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * The rules of one brief psychomotor vigilance test (PVT-B, Basner, Mollicone and Dinges 2011), apart from
 * the screen: three minutes in which a counter starts after a random wait and the athlete taps as soon as it
 * does. The time from one answer to the next counter is 1 to 4 seconds, of which the first second shows
 * the response time. A tap while no counter runs, or within 100 ms of one starting, is a false start:
 * "too soon" shows for a second and the wait starts again. A counter left for 30 seconds counts as a lapse.
 * After three minutes the test ends at the next wait.
 *
 * All times are milliseconds of one monotonic clock. The screen calls [frame] on every frame it draws, with
 * that frame's time, so that a counter starts with the first frame that shows it, and [tap] with the time
 * the touch happened.
 */
class ReactionTestRun(private val random: Random = Random.Default, val durationMillis: Long = DURATION_MILLIS) {
    sealed interface State {
        /** Nothing to answer; a counter starts at [until]. */
        data class Waiting(val until: Long) : State

        /** The counter runs from [onset]. */
        data class Stimulus(val onset: Long) : State

        /** The last response took [millis] (a lapse at [ReactionTest.TIMEOUT_MILLIS]); shown until [until], then a wait follows. */
        data class Feedback(val millis: Int, val until: Long, val next: Long) : State

        /** A tap came too soon; shown until [until], then a wait follows. */
        data class FalseStart(val until: Long, val next: Long) : State

        data object Finished : State
    }

    var state: State? = null
        private set

    private var started = 0L
    private val responses = mutableListOf<Int>()
    private var falseStarts = 0

    val responseCount: Int get() = responses.size

    fun start(now: Long) {
        check(state == null) { "Already started" }
        started = now
        state = State.Waiting(now + wait())
    }

    /** Share of the test's time gone at [now], 0 to 1. */
    fun progress(now: Long): Float = if (state == null) 0f else ((now - started).toFloat() / durationMillis).coerceIn(0f, 1f)

    /** Moves the test on to the frame drawn at [now]; returns the state that frame shows. */
    fun frame(now: Long): State {
        val current = checkNotNull(state) { "Not started" }
        val next = when (current) {
            is State.Waiting -> when {
                now - started >= durationMillis -> State.Finished
                now >= current.until -> State.Stimulus(now)
                else -> current
            }

            is State.Stimulus -> if (now - current.onset >= ReactionTest.TIMEOUT_MILLIS) {
                responses += ReactionTest.TIMEOUT_MILLIS
                feedback(ReactionTest.TIMEOUT_MILLIS, current.onset + ReactionTest.TIMEOUT_MILLIS)
            } else {
                current
            }

            is State.Feedback -> if (now >= current.until) afterPause(current.next, now) else current

            is State.FalseStart -> if (now >= current.until) afterPause(current.next, now) else current

            State.Finished -> current
        }
        state = next
        // A wait that is already over starts its counter in this same frame.
        if (next is State.Waiting && now >= next.until && now - started < durationMillis) state = State.Stimulus(now)
        return checkNotNull(state)
    }

    /** A touch at [at]; taps while a response time shows are ignored, as a second touch of the same answer. */
    fun tap(at: Long) {
        when (val current = state) {
            is State.Stimulus -> {
                val millis = (at - current.onset).toInt()
                if (millis < ReactionTest.FASTEST_MILLIS) {
                    falseStart(at)
                } else {
                    responses += millis.coerceAtMost(ReactionTest.TIMEOUT_MILLIS)
                    state = feedback(millis, at)
                }
            }

            is State.Waiting -> falseStart(at)

            is State.FalseStart, is State.Feedback, State.Finished, null -> Unit
        }
    }

    /** The finished test; null before it ends. */
    fun result(start: Instant, zoneOffset: ZoneOffset?): ReactionTest? =
        if (state == State.Finished) ReactionTest(start, zoneOffset, responses.toList(), falseStarts) else null

    /** The wait after a response or false start, or the end once the test's time is up. */
    private fun afterPause(next: Long, now: Long): State = if (now - started >= durationMillis) State.Finished else State.Waiting(next)

    private fun feedback(millis: Int, at: Long): State = State.Feedback(millis, at + FEEDBACK_MILLIS, at + FEEDBACK_MILLIS + pause())

    private fun falseStart(at: Long) {
        falseStarts++
        state = State.FalseStart(at + FEEDBACK_MILLIS, at + FEEDBACK_MILLIS + pause())
    }

    /** The whole gap from an answer to the next counter, 1 to 4 s. */
    private fun wait(): Long = random.nextLong(MIN_GAP_MILLIS, MAX_GAP_MILLIS + 1)

    /** The part of the gap after the second of feedback. */
    private fun pause(): Long = wait() - FEEDBACK_MILLIS

    companion object {
        const val DURATION_MILLIS = 180_000L
        const val MIN_GAP_MILLIS = 1_000L
        const val MAX_GAP_MILLIS = 4_000L
        const val FEEDBACK_MILLIS = 1_000L
    }
}
