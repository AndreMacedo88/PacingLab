package app.pacinglab.core.analytics.load

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.HeartRateSeries
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class HeartRateLoadTest {
    private val anchors = HeartRateAnchors(maxHr = 190, thresholdHr = 170, restingHr = 50)

    /** [minutes] of samples every 10 s at [bpm], plus a closing sample. */
    private fun steady(bpm: Int, minutes: Int, startMillis: Long = 0): HeartRateSeries {
        val n = minutes * 6 + 1
        return HeartRateSeries(LongArray(n) { startMillis + it * 10_000L }, IntArray(n) { bpm })
    }

    @Test
    fun `an hour at threshold scores 100`() {
        val load = HeartRateLoad.of(steady(170, 60), anchors)!!

        assertThat(load.stressScore).isCloseTo(100.0, 1e-9)
        assertThat(load.trimp).isCloseTo(170.651, 0.001)
        assertThat(load.heartRateMillis).isEqualTo(3_600_000L)
    }

    @Test
    fun `half an hour at 150 bpm matches the hand-computed TRIMP`() {
        // Reserve fraction (150 - 50) / 140 = 0.714; 30 min x 0.714 x 0.64 x e^(1.92 x 0.714) = 54.05.
        val load = HeartRateLoad.of(steady(150, 30), anchors)!!

        assertThat(load.trimp).isCloseTo(54.048, 0.001)
        assertThat(load.stressScore).isCloseTo(31.671, 0.001)
        // 150 / 190 = 78 % of HRmax: Edwards zone 3.
        assertThat(load.edwards).isCloseTo(90.0, 1e-9)
    }

    @Test
    fun `women's weighting differs from men's`() {
        val male = HeartRateLoad.of(steady(150, 30), anchors, BanisterWeighting.MALE)!!
        val female = HeartRateLoad.of(steady(150, 30), anchors, BanisterWeighting.FEMALE)!!

        // 30 x 0.714 x 0.86 x e^(1.67 x 0.714)
        assertThat(female.trimp).isCloseTo(60.75, 0.01)
        assertThat(female.trimp == male.trimp).isEqualTo(false)
    }

    @Test
    fun `heart rate below resting adds nothing and above maximum counts as maximum`() {
        assertThat(HeartRateLoad.of(steady(45, 10), anchors)!!.trimp).isEqualTo(0.0)
        assertThat(HeartRateLoad.of(steady(205, 10), anchors)!!.trimp)
            .isCloseTo(HeartRateLoad.of(steady(190, 10), anchors)!!.trimp, 1e-9)
    }

    @Test
    fun `Edwards zones are tenths of HRmax from 50 percent`() {
        assertThat(HeartRateLoad.edwardsZone(94, 190)).isEqualTo(0)
        assertThat(HeartRateLoad.edwardsZone(95, 190)).isEqualTo(1)
        assertThat(HeartRateLoad.edwardsZone(133, 190)).isEqualTo(3)
        assertThat(HeartRateLoad.edwardsZone(171, 190)).isEqualTo(5)
        assertThat(HeartRateLoad.edwardsZone(190, 190)).isEqualTo(5)
        assertThat(HeartRateLoad.edwardsZone(200, 190)).isEqualTo(5)
    }

    @Test
    fun `gaps count only up to the cap`() {
        val series = HeartRateSeries.of(0L to 170, 600_000L to 170)

        assertThat(HeartRateLoad.of(series, anchors)!!.heartRateMillis).isEqualTo(15_000L)
    }

    @Test
    fun `no resting heart rate means no load`() {
        assertThat(HeartRateLoad.of(steady(150, 30), anchors.copy(restingHr = null))).isNull()
    }

    @Test
    fun `empty and single-sample series have zero load`() {
        assertThat(HeartRateLoad.of(HeartRateSeries.EMPTY, anchors)).isEqualTo(LoadScores.ZERO)
        assertThat(HeartRateLoad.of(HeartRateSeries.of(0L to 150), anchors)).isEqualTo(LoadScores.ZERO)
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `load adds up over parts and never drops when heart rate rises`() = runTest {
        checkAll(PropTestConfig(seed = 20_261_021), Arb.list(Arb.int(40..200), 2..200), Arb.int(0..20)) { bpm, raise ->
            val series = HeartRateSeries(LongArray(bpm.size) { it * 2_000L }, bpm.toIntArray())
            val whole = HeartRateLoad.of(series, anchors)
            assertThat(whole).isNotNull()

            // Splitting at a sample that both halves share leaves the total unchanged.
            val k = bpm.size / 2
            val first = HeartRateSeries(series.times.copyOfRange(0, k + 1), series.bpm.copyOfRange(0, k + 1))
            val second = HeartRateSeries(series.times.copyOfRange(k, bpm.size), series.bpm.copyOfRange(k, bpm.size))
            val parts = HeartRateLoad.of(first, anchors)!! + HeartRateLoad.of(second, anchors)!!
            assertThat(parts.trimp).isCloseTo(whole!!.trimp, 1e-9)
            assertThat(parts.edwards).isCloseTo(whole.edwards, 1e-9)
            assertThat(parts.heartRateMillis).isEqualTo(whole.heartRateMillis)

            val higher = HeartRateLoad.of(HeartRateSeries(series.times, IntArray(bpm.size) { bpm[it] + raise }), anchors)!!
            assertThat(higher.trimp).isGreaterThanOrEqualTo(whole.trimp)
            assertThat(higher.edwards).isGreaterThanOrEqualTo(whole.edwards)
        }
    }
}
