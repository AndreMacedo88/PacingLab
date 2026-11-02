package app.pacinglab.core.analytics.home

import app.pacinglab.core.model.home.SensorStatistic
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class NightClimatesTest {
    private val from = Instant.parse("2026-11-02T23:30:00Z")
    private val to = Instant.parse("2026-11-03T06:30:00Z")

    private fun hours(vararg means: Double?) = means.mapIndexed { i, mean ->
        val start = Instant.parse("2026-11-02T23:00:00Z").plusSeconds(3_600L * i)
        SensorStatistic(start, start.plusSeconds(3_600), mean, mean?.minus(0.2), mean?.plus(0.2))
    }

    @Test
    fun `hours count by how much of them the night covers`() {
        // 23:00 to 07:00: the first and last hours count half.
        val temperature = hours(22.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0, 20.0)

        // (0.5 x 22 + 6.5 x 20) / 7
        assertThat(NightClimates.mean(temperature, from, to)!!).isCloseTo(20.142857, 1e-6)
    }

    @Test
    fun `a night mostly without values has no mean, and the CO2 maximum comes from the night only`() {
        val gaps = hours(19.0, null, null, null, null, null, 19.5, 19.4)
        val co2 = hours(700.0, 900.0, 1100.0, 1200.0, 1150.0, 1000.0, 950.0, 2000.0)

        val night = NightClimates.of(LocalDate.of(2026, 11, 3), from, to, gaps, null, co2)

        assertThat(night.temperature).isNull()
        assertThat(night.humidity).isNull()
        // The hour from 06:00 overlaps the night; its maximum counts.
        assertThat(night.co2Max).isEqualTo(2000.2)
        assertThat(NightClimates.max(co2, from, Instant.parse("2026-11-03T05:00:00Z"))).isEqualTo(1200.2)
    }
}
