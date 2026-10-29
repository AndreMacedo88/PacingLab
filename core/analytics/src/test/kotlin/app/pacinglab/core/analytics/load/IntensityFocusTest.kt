package app.pacinglab.core.analytics.load

import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.analytics.zones.Zones
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.ZoneModel
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class IntensityFocusTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val minute = 60_000L
    private val zones = Zones.of(ZoneModel.POLARIZED_THREE, HeartRateAnchors(maxHr = 186, thresholdHr = 167, restingHr = 50))

    private fun load(daysAgo: Long, below: Long, between: Long, above: Long, sport: Sport = Sport.RUN) = WorkoutLoad(
        workoutId = "w$daysAgo$sport",
        date = today.minusDays(daysAgo),
        durationMillis = (below + between + above) * minute,
        scores = null,
        intensity = ZoneDistribution(zones, longArrayOf(below * minute, between * minute, above * minute)),
        sport = sport,
    )

    @Test
    fun `a polarized month is within every band`() {
        val loads = (0L until 28L step 2).map { load(it, below = 48, between = 3, above = 9) }

        val focus = IntensityFocus.of(loads, today)!!

        assertThat(focus.share(IntensityDomain.BELOW_VT1).share).isCloseTo(0.8, 1e-9)
        assertThat(focus.mainGap).isNull()
        assertThat(focus.model).isEqualTo(DistributionModel.POLARIZED)
    }

    @Test
    fun `threshold heavy training is short of easy time`() {
        val loads = (0L until 28L step 2).map { load(it, below = 25, between = 25, above = 10) }

        val focus = IntensityFocus.of(loads, today)!!

        assertThat(focus.share(IntensityDomain.BELOW_VT1).standing).isEqualTo(FocusStanding.SHORT)
        assertThat(focus.share(IntensityDomain.VT1_TO_VT2).standing).isEqualTo(FocusStanding.OVER)
        assertThat(focus.mainGap!!.domain).isEqualTo(IntensityDomain.BELOW_VT1)
        assertThat(focus.model).isEqualTo(DistributionModel.THRESHOLD)
    }

    @Test
    fun `only runs and rides of the last four weeks count`() {
        val loads = listOf(
            load(0, below = 120, between = 10, above = 10),
            load(1, below = 0, between = 0, above = 600, sport = Sport.STRENGTH),
            load(40, below = 0, between = 600, above = 0),
        )

        val focus = IntensityFocus.of(loads, today)!!

        assertThat(focus.share(IntensityDomain.ABOVE_VT2).millis).isEqualTo(10 * minute)
        assertThat(focus.model).isEqualTo(DistributionModel.PYRAMIDAL)
    }

    @Test
    fun `too little training gives no focus`() {
        assertThat(IntensityFocus.of(listOf(load(0, below = 30, between = 10, above = 10)), today)).isNull()
    }
}
