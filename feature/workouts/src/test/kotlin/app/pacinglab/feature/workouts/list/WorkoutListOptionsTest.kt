package app.pacinglab.feature.workouts.list

import app.pacinglab.core.model.SessionKind
import app.pacinglab.core.model.Sport
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test
import java.time.Instant

class WorkoutListOptionsTest {
    private fun item(
        id: String,
        sport: Sport,
        kind: SessionKind? = null,
        capture: Capture = Capture.RECORDED,
        minutes: Long = 30,
        meters: Double? = null,
    ) = SessionItem(
        id = id,
        sport = sport,
        titles = emptyList(),
        parts = 1,
        start = Instant.EPOCH,
        zoneOffset = null,
        movingMillis = minutes * 60_000,
        distanceMeters = meters,
        averageHr = null,
        maxHr = null,
        hasRoute = false,
        capture = capture,
        origin = if (capture == Capture.RECORDED) "com.fitbit.FitbitMobile" else "com.google.android.apps.fitness",
        kind = kind,
    )

    // Newest first, as the list receives them.
    private val sessions = listOf(
        item("intervals", Sport.RUN, SessionKind.THRESHOLD_INTERVALS, meters = 7_000.0),
        item("weights", Sport.STRENGTH, minutes = 70, meters = 9_000.0),
        item("walk", Sport.WALK, capture = Capture.DETECTED, minutes = 40, meters = 3_000.0),
        item("easy", Sport.RUN, SessionKind.EASY_RUN, minutes = 50, meters = 9_500.0),
        item("ride", Sport.RIDE, capture = Capture.DETECTED, minutes = 20),
    )

    @Test
    fun `a filter needs one value of every group it restricts`() {
        val runs = WorkoutFilter(types = setOf(SessionType.INTERVALS, SessionType.EASY_RUNS))
        val detected = WorkoutFilter(captures = setOf(Capture.DETECTED))

        assertThat(sessions.arranged(runs, WorkoutSort.NEWEST).map { it.id }).containsExactly("intervals", "easy")
        assertThat(sessions.arranged(detected, WorkoutSort.OLDEST).map { it.id }).containsExactly("ride", "walk")
        assertThat(sessions.arranged(runs.copy(captures = detected.captures), WorkoutSort.NEWEST)).isEqualTo(emptyList())
    }

    @Test
    fun `distance order leaves out the steps of gym sessions and puts sessions without distance last`() {
        assertThat(sessions.arranged(WorkoutFilter(), WorkoutSort.FARTHEST).map { it.id })
            .containsExactly("easy", "intervals", "walk", "weights", "ride")
        assertThat(sessions.arranged(WorkoutFilter(), WorkoutSort.LONGEST).map { it.id })
            .containsExactly("weights", "easy", "walk", "intervals", "ride")
    }

    @Test
    fun `a filter survives being stored by name, and unknown names are skipped`() {
        val filter = WorkoutFilter(setOf(SessionType.WALKS), setOf(Capture.DETECTED), setOf("com.google.android.apps.fitness"))

        assertThat(WorkoutFilter.of(filter.names() + "type:SWIMS")).isEqualTo(filter)
    }
}
