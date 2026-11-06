package app.pacinglab.core.analytics.alertness

import app.pacinglab.core.analytics.alertness.ReactionTestRun.State
import app.pacinglab.core.model.ReactionTest
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random

class ReactionTestRunTest {
    private val frame = 16L

    /** Runs a whole test, answering every counter after [answer] ms (or never, when null), from a 16 ms frame clock. */
    private fun play(run: ReactionTestRun, answer: Long?, until: Long = Long.MAX_VALUE): Long {
        var now = 1_000L
        run.start(now)
        var due: Long? = null
        while (now < until) {
            val state = run.frame(now)
            if (state == State.Finished) break
            if (state is State.Stimulus && due == null && answer != null) due = state.onset + answer
            if (due != null && now + frame > due) {
                run.tap(due)
                due = null
            }
            now += frame
        }
        return now
    }

    @Test
    fun `three minutes of answers give a test with one response per counter`() {
        val run = ReactionTestRun(Random(1))
        val end = play(run, answer = 260)
        val test = checkNotNull(run.result(Instant.EPOCH, null))
        assertThat(test.reactionMillis).each { it.isEqualTo(260) }
        assertThat(test.falseStarts).isEqualTo(0)
        // A gap of 1 to 4 s plus the answer: between 180 / 4.26 and 180 / 1.26 counters.
        assertThat(test.reactionMillis.size).isBetween(42, 143)
        assertThat(end - 1_000L).isBetween(ReactionTestRun.DURATION_MILLIS, ReactionTestRun.DURATION_MILLIS + 4_000L + frame)
    }

    @Test
    fun `the gap between an answer and the next counter is 1 to 4 seconds`() {
        val run = ReactionTestRun(Random(7))
        run.start(0)
        var now = 0L
        var answeredAt: Long? = null
        repeat(5_000) {
            val state = run.frame(now)
            if (state is State.Stimulus && answeredAt != null) {
                assertThat(state.onset - answeredAt).isBetween(1_000L, 4_000L + frame)
                answeredAt = null
            }
            if (state is State.Stimulus && now - state.onset >= 300) {
                run.tap(now)
                answeredAt = now
            }
            now += frame
        }
    }

    @Test
    fun `a tap while waiting, or within 100 ms of the counter, is a false start`() {
        val run = ReactionTestRun(Random(3))
        run.start(0)
        run.frame(10)
        run.tap(20)
        assertThat(run.state).isNotNull().isInstanceOf<State.FalseStart>()
        var now = 20L
        var state: State
        do {
            now += frame
            state = run.frame(now)
        } while (state !is State.Stimulus)
        run.tap(state.onset + 50)
        assertThat(run.state).isNotNull().isInstanceOf<State.FalseStart>()
        assertThat(run.responseCount).isEqualTo(0)
    }

    @Test
    fun `taps during the shown response time are ignored`() {
        val run = ReactionTestRun(Random(5))
        run.start(0)
        var now = 0L
        var state: State
        do {
            now += frame
            state = run.frame(now)
        } while (state !is State.Stimulus)
        run.tap(state.onset + 280)
        run.tap(state.onset + 400)
        assertThat(run.state).isNotNull().isInstanceOf<State.Feedback>().prop(State.Feedback::millis).isEqualTo(280)
        assertThat(run.responseCount).isEqualTo(1)
    }

    @Test
    fun `an unanswered counter ends after 30 seconds as a lapse`() {
        val run = ReactionTestRun(Random(2))
        play(run, answer = null, until = 40_000)
        assertThat(run.responseCount).isEqualTo(1)
        assertThat(run.result(Instant.EPOCH, null)).isNull()
        val lapse = ReactionTestRun(Random(2))
        play(lapse, answer = null)
        assertThat(checkNotNull(lapse.result(Instant.EPOCH, null)).reactionMillis).each { it.isEqualTo(ReactionTest.TIMEOUT_MILLIS) }
    }
}
