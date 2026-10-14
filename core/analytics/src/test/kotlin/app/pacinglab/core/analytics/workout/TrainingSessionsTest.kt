package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.Sport
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.triple
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration

class TrainingSessionsTest {
    @Test
    fun `warm-up run and interval workout four minutes apart become one session`() {
        val warmUp = workout("run", Sport.RUN, startMinute = 0, minutes = 6)
        val intervals = workout("intervals", Sport.RUN, startMinute = 10, minutes = 30)

        val sessions = TrainingSessions.group(listOf(intervals, warmUp))

        assertThat(sessions.size).isEqualTo(1)
        assertThat(sessions[0].workouts.map { it.id }).containsExactly("run", "intervals")
        assertThat(sessions[0].id).isEqualTo("run")
    }

    @Test
    fun `a gap longer than the limit keeps workouts apart`() {
        val first = workout("a", Sport.RUN, startMinute = 0, minutes = 6)
        val second = workout("b", Sport.RUN, startMinute = 12, minutes = 30)

        assertThat(TrainingSessions.group(listOf(first, second)).size).isEqualTo(2)
        assertThat(TrainingSessions.group(listOf(first, second), maxGap = Duration.ofMinutes(6)).size).isEqualTo(1)
    }

    @Test
    fun `different sports are never merged`() {
        val run = workout("run", Sport.RUN, startMinute = 0, minutes = 30)
        val weights = workout("weights", Sport.STRENGTH, startMinute = 31, minutes = 60)

        assertThat(TrainingSessions.group(listOf(run, weights)).size).isEqualTo(2)
    }

    @Test
    fun `grouping keeps every workout exactly once, in order, with one sport per session`() = runTest {
        val spec = Arb.triple(Arb.long(0L..2_000L), Arb.long(1L..120L), Arb.enum<Sport>())
        checkAll(Arb.list(spec, 0..30), Arb.int(0..30)) { specs, gapMinutes ->
            val workouts = specs.mapIndexed { i, (start, length, sport) -> workout("w$i", sport, start, length) }

            val sessions = TrainingSessions.group(workouts, Duration.ofMinutes(gapMinutes.toLong()))

            assertThat(sessions.flatMap { it.workouts }.map { it.id }.sorted()).isEqualTo(workouts.map { it.id }.sorted())
            assertThat(sessions.zipWithNext().all { (a, b) -> !a.start.isAfter(b.start) }).isTrue()
            assertThat(sessions.all { session -> session.workouts.all { it.sport == session.sport } }).isTrue()
        }
    }

    @Test
    fun `accidental starts under a minute are left out`() {
        val falseStart = workout("false-start", Sport.OTHER, startMinute = 0, minutes = 0)
        val intervals = workout("intervals", Sport.RUN, startMinute = 0, minutes = 30)

        assertThat(TrainingSessions.group(listOf(falseStart, intervals)).flatMap { s -> s.workouts.map { it.id } })
            .containsExactly("intervals")
    }

    @Test
    fun `the same ride recorded by two apps is kept once, preferring the one with heart rate`() {
        val fitbit = workout("fitbit", Sport.RIDE, startMinute = 0, minutes = 20).withHr(130)
        val googleFit = workout("google", Sport.RIDE, startMinute = 2, minutes = 20).copy(origin = "com.google.android.apps.fitness")

        assertThat(TrainingSessions.removeDuplicates(listOf(googleFit, fitbit)).map { it.id }).containsExactly("fitbit")
    }

    @Test
    fun `back-to-back workouts from the same app are not duplicates`() {
        val run = workout("run", Sport.RUN, startMinute = 0, minutes = 6)
        val intervals = workout("intervals", Sport.RUN, startMinute = 5, minutes = 30)

        assertThat(TrainingSessions.removeDuplicates(listOf(run, intervals)).size).isEqualTo(2)
    }

    private fun app.pacinglab.core.model.Workout.withHr(bpm: Int) = copy(summary = summary.copy(averageHr = bpm))
}
