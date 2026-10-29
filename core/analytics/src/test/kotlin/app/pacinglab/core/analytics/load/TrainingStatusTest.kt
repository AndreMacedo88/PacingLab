package app.pacinglab.core.analytics.load

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TrainingStatusTest {
    private val today = LocalDate.of(2026, 11, 20)

    /** Ninety days of steady training at [usual] a day, then the last [days] days at [recent] a day. */
    private fun series(usual: Double = 60.0, recent: Double = usual, days: Int = 7): List<FormDay> {
        val loads = (89 downTo 0).associate { ago ->
            today.minusDays(ago.toLong()) to if (ago < days) recent else usual
        }
        return FitnessModel.series(loads, today)
    }

    @Test
    fun `steady load with VO2max rising is productive`() {
        assertThat(TrainingStatus.of(series(), vo2maxChange = 1.0, hrvBelowNormal = false)!!.kind)
            .isEqualTo(TrainingStatusKind.PRODUCTIVE)
    }

    @Test
    fun `steady load with VO2max steady or unknown is maintaining`() {
        assertThat(TrainingStatus.of(series(), vo2maxChange = 0.2, hrvBelowNormal = false)!!.kind)
            .isEqualTo(TrainingStatusKind.MAINTAINING)
        assertThat(TrainingStatus.of(series(), vo2maxChange = null, hrvBelowNormal = null)!!.kind)
            .isEqualTo(TrainingStatusKind.MAINTAINING)
    }

    @Test
    fun `steady load with VO2max falling is unproductive`() {
        assertThat(TrainingStatus.of(series(), vo2maxChange = -1.0, hrvBelowNormal = false)!!.kind)
            .isEqualTo(TrainingStatusKind.UNPRODUCTIVE)
    }

    @Test
    fun `a very hard week is overreaching`() {
        val status = TrainingStatus.of(series(recent = 200.0), vo2maxChange = 1.0, hrvBelowNormal = false)!!

        assertThat(status.kind).isEqualTo(TrainingStatusKind.OVERREACHING)
    }

    @Test
    fun `HRV below normal while training is strained`() {
        assertThat(TrainingStatus.of(series(), vo2maxChange = 1.0, hrvBelowNormal = true)!!.kind)
            .isEqualTo(TrainingStatusKind.STRAINED)
    }

    @Test
    fun `an easy week after steady training is peaking, or recovery when VO2max falls`() {
        assertThat(TrainingStatus.of(series(recent = 30.0), vo2maxChange = 0.0, hrvBelowNormal = false)!!.kind)
            .isEqualTo(TrainingStatusKind.PEAKING)
        assertThat(TrainingStatus.of(series(recent = 30.0), vo2maxChange = -1.0, hrvBelowNormal = false)!!.kind)
            .isEqualTo(TrainingStatusKind.RECOVERY)
    }

    @Test
    fun `weeks without training are detraining`() {
        assertThat(TrainingStatus.of(series(recent = 0.0, days = 21), vo2maxChange = -1.0, hrvBelowNormal = null)!!.kind)
            .isEqualTo(TrainingStatusKind.DETRAINING)
    }

    @Test
    fun `too little history or training gives no status`() {
        assertThat(TrainingStatus.of(series().takeLast(20), vo2maxChange = 1.0, hrvBelowNormal = false)).isNull()
        assertThat(TrainingStatus.of(series(usual = 5.0), vo2maxChange = 1.0, hrvBelowNormal = false)).isNull()
    }
}
