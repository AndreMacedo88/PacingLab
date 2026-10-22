package app.pacinglab.core.analytics.recovery

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FeelingAgreementTest {
    private val day = LocalDate.of(2026, 11, 1)

    private fun score(value: Int) =
        SleepScore(value, duration = 1.0, continuity = 1.0, regularity = 1.0, timing = 1.0, midpointMinuteOfDay = 180)

    @Test
    fun `ties share the mean of their ranks`() {
        assertThat(Spearman.ranks(listOf(3.0, 1.0, 3.0, 2.0)).toList()).containsExactly(3.5, 1.0, 3.5, 2.0)
    }

    @Test
    fun `rho matches a worked example`() {
        // Ranks 1..5 against 2,1,4,3,5: sum of squared differences 4, rho = 1 - 6 x 4 / (5 x 24) = 0.8.
        val rho = Spearman.rho(listOf(10.0, 20.0, 30.0, 40.0, 50.0), listOf(2.0, 1.0, 4.0, 3.0, 5.0))

        assertThat(rho).isNotNull().isCloseTo(0.8, 1e-9)
    }

    @Test
    fun `a constant side has no correlation`() {
        assertThat(Spearman.rho(listOf(1.0, 2.0, 3.0), listOf(4.0, 4.0, 4.0))).isNull()
    }

    @Test
    fun `mornings pair a rating with the night that ended on it`() {
        val scores = mapOf(day to score(80), day.plusDays(1) to score(70))
        val rested = mapOf(day.plusDays(1) to 2, day to 4, day.plusDays(2) to 5)

        val agreement = FeelingAgreement.of(scores, rested)

        assertThat(agreement.mornings).containsExactly(RatedMorning(day, 80, 4), RatedMorning(day.plusDays(1), 70, 2))
        assertThat(agreement.rho).isNull()
    }

    @Test
    fun `from ten mornings the agreement is measured and named`() {
        val pairs = listOf(62 to 2, 70 to 3, 74 to 3, 78 to 4, 81 to 3, 84 to 4, 86 to 5, 88 to 4, 90 to 5, 66 to 2)
        val scores = pairs.indices.associate { day.plusDays(it.toLong()) to score(pairs[it].first) }
        val rested = pairs.indices.associate { day.plusDays(it.toLong()) to pairs[it].second }

        val agreement = FeelingAgreement.of(scores, rested)

        assertThat(agreement.rho).isNotNull().isCloseTo(0.900, 0.001)
        assertThat(agreement.strength).isEqualTo(FeelingAgreement.Strength.STRONG)
    }

    @Test
    fun `a score that runs against the feeling is called opposite`() {
        val pairs = (0 until 10).map { 60 + 3 * it to 5 - it / 2 }
        val scores = pairs.indices.associate { day.plusDays(it.toLong()) to score(pairs[it].first) }
        val rested = pairs.indices.associate { day.plusDays(it.toLong()) to pairs[it].second }

        assertThat(FeelingAgreement.of(scores, rested).strength).isEqualTo(FeelingAgreement.Strength.OPPOSITE)
    }
}
