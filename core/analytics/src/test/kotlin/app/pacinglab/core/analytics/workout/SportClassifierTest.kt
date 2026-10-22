package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SportClassifierTest {
    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(
        "running, RUN",
        "running_treadmill, RUN",
        "walking, WALK",
        "hiking, WALK",
        "biking, RIDE",
        "strength_training, STRENGTH",
        "weightlifting, STRENGTH",
        "yoga, OTHER",
    )
    fun `specific activity types map directly`(activity: String, sport: Sport) {
        assertThat(SportClassifier.classify(activity, SampleSeries.EMPTY)).isEqualTo(sport)
    }

    @Test
    fun `interval workout with running reps and walking recoveries is a run`() {
        val reps = Array(5) { listOf(240 to 4.1, 120 to 0.7) }.flatMap { it }.toTypedArray()

        assertThat(SportClassifier.classify("boot_camp", speedBlocks(*reps))).isEqualTo(Sport.RUN)
    }

    @Test
    fun `generic workout without speed stays other`() {
        assertThat(SportClassifier.classify("boot_camp", SampleSeries.EMPTY)).isEqualTo(Sport.OTHER)
    }

    @Test
    fun `generic workout at walking pace stays other`() {
        assertThat(SportClassifier.classify("boot_camp", speedBlocks(1_800 to 1.4))).isEqualTo(Sport.OTHER)
    }

    @Test
    fun `generic workout at cycling speed is a ride`() {
        assertThat(SportClassifier.classify("other_workout", speedBlocks(1_800 to 8.5))).isEqualTo(Sport.RIDE)
    }

    @Test
    fun `GPS spikes alone do not make a run`() {
        val spiky = speedBlocks(600 to 0.3, 5 to 88.0, 600 to 0.3)

        assertThat(SportClassifier.classify("boot_camp", spiky)).isEqualTo(Sport.OTHER)
    }

    @Test
    fun `less than three minutes of running is not enough`() {
        assertThat(SportClassifier.classify("boot_camp", speedBlocks(120 to 3.5, 600 to 1.0))).isEqualTo(Sport.OTHER)
    }

    @Test
    fun `sprints with walking rests are a run despite little running time`() {
        val sprint = arrayOf(2 to 3.5, 10 to 8.5, 2 to 3.5, 150 to 1.1)
        val session = arrayOf(30 to 1.2) + sprint + sprint + sprint

        assertThat(SportClassifier.classify("boot_camp", speedBlocks(*session))).isEqualTo(Sport.RUN)
    }

    @Test
    fun `without speed, the recorded distance decides`() {
        val thirtyMinutes = 30 * 60_000L

        // 5.6 km in 30 min including walking recoveries: an interval run.
        assertThat(SportClassifier.classify("boot_camp", SampleSeries.EMPTY, 5_600.0, thirtyMinutes)).isEqualTo(Sport.RUN)
        // Gym session recorded in interval mode: barely any movement.
        assertThat(SportClassifier.classify("boot_camp", SampleSeries.EMPTY, 800.0, 64 * 60_000L)).isEqualTo(Sport.OTHER)
        // No distance at all.
        assertThat(SportClassifier.classify("boot_camp", SampleSeries.EMPTY, null, thirtyMinutes)).isEqualTo(Sport.OTHER)
    }
}
