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
        // Ten nights alternating 50 and 72 ms, resting heart rate 55.
        val nights = (9 downTo 0).map { back -> night(today.minusDays(back.toLong()), if (back % 2 == 0) 50.0 else 72.0, 55.0) }

        val days = RecoveryTrend.of(nights, today)

        assertThat(days.size).isEqualTo(10)
        assertThat(days[1].hrvWeekMs).isNull()
        // Day 7 (index 6) averages 72, 50, 72, 50, 72, 50, 72 on the log scale.
        assertThat(days[6].hrvWeekMs!!).isCloseTo(exp((4 * ln(72.0) + 3 * ln(50.0)) / 7), 1e-9)
        assertThat(days[5].hrvNormal).isNull()
        assertThat(days[9].hrvNormal).isNotNull()
        assertThat(days[9].restingWeek!!).isCloseTo(55.0, 1e-9)
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
