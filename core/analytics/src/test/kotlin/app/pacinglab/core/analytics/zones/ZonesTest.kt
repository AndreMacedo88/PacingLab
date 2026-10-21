package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyEntry
import app.pacinglab.core.model.ZoneModel
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class ZonesTest {
    private val anchors = HeartRateAnchors(maxHr = 190, thresholdHr = 170, restingHr = 50)

    @Test
    fun `LTHR zones follow Friel percentages with 5a folded into zone 4`() {
        val zones = Zones.of(ZoneModel.LTHR_FIVE, anchors)

        assertThat(zones.zones.map { it.lowerBpm }).containsExactly(0, 145, 153, 162, 175)
        assertThat(zones.zones.map { it.label })
            .containsExactly("Recovery", "Aerobic", "Tempo", "Threshold", "VO2max")
    }

    @Test
    fun `heart rate reserve zones are offset by resting heart rate`() {
        val zones = Zones.of(ZoneModel.HEART_RATE_RESERVE_FIVE, anchors)

        assertThat(zones.zones.map { it.lowerBpm }).containsExactly(0, 134, 148, 162, 176)
    }

    @Test
    fun `polarized zones split at 90 percent and 100 percent of LTHR`() {
        val zones = Zones.of(ZoneModel.POLARIZED_THREE, anchors)

        assertThat(zones.zones.map { it.lowerBpm }).containsExactly(0, 153, 170)
    }

    @ParameterizedTest(name = "{0} bpm is in zone index {1}")
    @CsvSource("40, 0", "144, 0", "145, 1", "152, 1", "153, 2", "161, 2", "162, 3", "174, 3", "175, 4", "220, 4")
    fun `heart rate maps to the zone whose lower bound it reaches`(bpm: Int, expectedIndex: Int) {
        val zones = Zones.of(ZoneModel.LTHR_FIVE, anchors)

        assertThat(zones.indexOf(bpm)).isEqualTo(expectedIndex)
    }

    @Test
    fun `upper bound of a zone is the next zone's lower bound and the top zone is open`() {
        val zones = Zones.of(ZoneModel.LTHR_FIVE, anchors)

        assertThat(zones.upperBpm(0)).isEqualTo(145)
        assertThat(zones.upperBpm(4)).isEqualTo(null)
    }

    @Test
    fun `every model yields strictly increasing bounds for any plausible anchors`() = runTest {
        checkAll(Arb.int(140..220), Arb.int(35..75), Arb.int(80..95)) { maxHr, restingHr, thresholdPercent ->
            val threshold = maxHr * thresholdPercent / 100
            val plausible = HeartRateAnchors(maxHr = maxHr, thresholdHr = threshold, restingHr = restingHr)

            // ZoneSet's constructor enforces ordering, so building each model is the assertion.
            ZoneModel.entries.forEach { Zones.of(it, plausible) }
        }
    }

    @Test
    fun `LTHR estimate from max heart rate is 90 percent rounded`() {
        assertThat(ThresholdEstimates.thresholdFromMax(193)).isEqualTo(174)
    }

    @Test
    fun `anchors use the athlete's LTHR when set and the estimate otherwise`() {
        val day = LocalDate.of(2026, 10, 21)

        assertThat(ThresholdEstimates.anchors(PhysiologyEntry(day, maxHr = 186)).thresholdHr).isEqualTo(167)
        assertThat(ThresholdEstimates.anchors(PhysiologyEntry(day, maxHr = 186, thresholdHr = 171)).thresholdHr).isEqualTo(171)
    }

    @Test
    fun `heart rate reserve zones need a resting heart rate`() {
        assertFailure { Zones.of(ZoneModel.HEART_RATE_RESERVE_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167)) }
        Zones.of(ZoneModel.LTHR_FIVE, HeartRateAnchors(maxHr = 186, thresholdHr = 167))
    }
}
