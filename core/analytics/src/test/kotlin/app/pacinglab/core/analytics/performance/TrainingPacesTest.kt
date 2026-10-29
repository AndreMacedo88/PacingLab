package app.pacinglab.core.analytics.performance

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class TrainingPacesTest {
    /** Seconds per kilometre at [speed] m/s. */
    private fun pace(speed: Double) = 1_000 / speed

    @Test
    fun `VO2max 50 reproduces Daniels' table`() {
        // VDOT 50 in Daniels' Running Formula: marathon 4:31, threshold 4:15, interval 3:55, repetition 3:42 per km.
        val paces = TrainingPaceCalculator.of(50.0)!!

        assertThat(pace(paces.marathon)).isCloseTo(4 * 60 + 31.0, 2.0)
        assertThat(pace(paces.threshold)).isCloseTo(4 * 60 + 15.0, 2.0)
        assertThat(pace(paces.interval)).isCloseTo(3 * 60 + 55.0, 2.0)
        assertThat(pace(paces.repetition)).isCloseTo(3 * 60 + 42.0, 2.0)
    }

    @Test
    fun `easy pace spans 59 to 74 percent of VO2max`() {
        val paces = TrainingPaceCalculator.of(50.0)!!

        assertThat(pace(paces.easy.start)).isCloseTo(5 * 60 + 52.0, 3.0)
        assertThat(pace(paces.easy.endInclusive)).isCloseTo(4 * 60 + 53.0, 3.0)
        assertThat(paces.easy.endInclusive).isLessThan(paces.marathon)
    }

    @Test
    fun `paces get faster from easy to repetition`() {
        val paces = TrainingPaceCalculator.of(55.0)!!

        assertThat(paces.marathon).isGreaterThan(paces.easy.endInclusive)
        assertThat(paces.threshold).isGreaterThan(paces.marathon)
        assertThat(paces.interval).isGreaterThan(paces.threshold)
        assertThat(paces.repetition).isGreaterThan(paces.interval)
    }

    @Test
    fun `the speed at an oxygen cost inverts Daniels' cost equation`() {
        val speed = TrainingPaceCalculator.speedAt(40.0)
        val metersPerMinute = speed * 60

        assertThat(-4.60 + 0.182258 * metersPerMinute + 0.000104 * metersPerMinute * metersPerMinute).isCloseTo(40.0, 1e-9)
    }

    @Test
    fun `no paces outside the model's range`() {
        assertThat(TrainingPaceCalculator.of(10.0)).isNull()
    }
}
