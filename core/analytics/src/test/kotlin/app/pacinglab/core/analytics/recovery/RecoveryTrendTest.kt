package app.pacinglab.core.analytics.recovery

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.exp
import kotlin.math.ln

class RecoveryTrendTest {
    private val today = LocalDate.of(2026, 10, 22)

    private fun night(date: LocalDate, hrv: Double, resting: Double): Night {
        val start = Instant.parse("${date.minusDays(1)}T22:00:00Z")
        return Night(
            date = date,
            start = start,
            end = start.plusSeconds(8 * 3_600L),
            asleepMillis = 7 * 3_600_000L,
            stageMillis = emptyMap(),
            hrvMs = hrv,
            restingHr = resting,
            respiratoryRate = 12.0,
            continuity = null,
            zone = ZoneOffset.UTC,
        )
    }

    @Test
    fun `every day from the first night gets its weekly averages and, once there is a baseline, its ranges`() {
        // Fourteen nights alternating 50 and 72 ms, resting heart rate 55.
        val nights = (13 downTo 0).map { back -> night(today.minusDays(back.toLong()), if (back % 2 == 0) 50.0 else 72.0, 55.0) }

        val days = RecoveryTrend.of(nights, today)

        assertThat(days.size).isEqualTo(14)
        assertThat(days[1].hrvRecentMs).isNull()
        // Day 7 (index 6) averages 72, 50, 72, 50, 72, 50, 72 on the log scale, weighted 1 to 7 from the oldest:
        // the 72s carry 1 + 3 + 5 + 7 = 16 of 28.
        assertThat(days[6].hrvRecentMs!!).isCloseTo(exp((16 * ln(72.0) + 12 * ln(50.0)) / 28), 1e-9)
        // The band needs a week of nights before the recent week: the seven of days 1 to 7 serve day 14 first.
        assertThat(days[12].hrvNormal).isNull()
        assertThat(days[13].hrvNormal).isNotNull()
        assertThat(days[13].restingWeek!!).isCloseTo(55.0, 1e-9)
    }

    @Test
    fun `a stray night weeks before the others is left out`() {
        val nights = listOf(night(today.minusDays(48), 40.0, 70.0)) + (4 downTo 0).map { night(today.minusDays(it.toLong()), 60.0, 55.0) }

        val days = RecoveryTrend.of(nights, today)

        assertThat(days.first().date).isEqualTo(today.minusDays(4))
    }

    @Test
    fun `no nights give no days`() {
        assertThat(RecoveryTrend.of(emptyList(), today)).isEmpty()
    }
}
