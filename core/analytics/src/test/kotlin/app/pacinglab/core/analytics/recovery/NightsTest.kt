package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.Measurement
import app.pacinglab.core.model.MeasurementKind
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class NightsTest {
    private val cest = ZoneOffset.ofHours(2)

    private fun sleep(id: String, start: String, end: String, vararg stages: Pair<SleepStageType, Long>): SleepSession {
        val begin = Instant.parse(start)
        var t = begin
        val list = stages.map { (type, minutes) ->
            SleepStage(t, t.plus(Duration.ofMinutes(minutes)), type).also { t = it.end }
        }
        return SleepSession(id, begin, Instant.parse(end), cest, "com.fitbit.FitbitMobile", list)
    }

    private fun value(kind: MeasurementKind, time: String, value: Double) =
        Measurement("$kind-$time", kind, Instant.parse(time), cest, value, "com.fitbit.FitbitMobile")

    /** Five-minute HRV values from [start], one per entry. */
    private fun hrv(start: String, vararg ms: Double) = ms.mapIndexed { i, v ->
        value(MeasurementKind.HRV_RMSSD, Instant.parse(start).plus(Duration.ofMinutes(5L * i)).toString(), v)
    }

    @Test
    fun `a night takes the main sleep of the morning with its median HRV and nightly values`() {
        val night = sleep(
            "n",
            "2026-10-20T21:08:00Z",
            "2026-10-21T04:38:00Z",
            SleepStageType.LIGHT to 200,
            SleepStageType.DEEP to 80,
            SleepStageType.AWAKE to 30,
            SleepStageType.REM to 140,
        )
        val nap = sleep("nap", "2026-10-21T12:00:00Z", "2026-10-21T13:00:00Z", SleepStageType.LIGHT to 60)
        val hrv = hrv("2026-10-20T22:00:00Z", 50.0, 70.0, 57.6, 61.0, 44.0, 57.6, 60.0) +
            // Daytime values do not belong to the night.
            hrv("2026-10-21T10:00:00Z", 20.0, 20.0, 20.0)
        val resting = listOf(
            value(MeasurementKind.RESTING_HEART_RATE, "2026-10-21T04:38:00Z", 57.0),
            // Derived from daytime wear on a day without night data: not a nightly value.
            value(MeasurementKind.RESTING_HEART_RATE, "2026-10-19T22:00:00Z", 78.0),
        )
        val breathing = listOf(value(MeasurementKind.RESPIRATORY_RATE, "2026-10-21T04:38:00Z", 13.2))

        val nights = Nights.of(listOf(nap, night), hrv, resting, breathing, cest)

        val only = nights.single()
        assertThat(only.date).isEqualTo(LocalDate.of(2026, 10, 21))
        assertThat(only.asleepMillis).isEqualTo(420 * 60_000L)
        assertThat(only.stageMillis[SleepStageType.AWAKE]).isEqualTo(30 * 60_000L)
        assertThat(only.hrvMs).isEqualTo(57.6)
        assertThat(only.restingHr).isEqualTo(57.0)
        assertThat(only.respiratoryRate).isEqualTo(13.2)
    }

    @Test
    fun `daytime resting heart rate is never taken for a night`() {
        val night = sleep("n", "2026-10-19T20:00:00Z", "2026-10-20T04:00:00Z", SleepStageType.LIGHT to 480)
        val resting = listOf(value(MeasurementKind.RESTING_HEART_RATE, "2026-10-19T22:00:00Z", 78.0))

        assertThat(Nights.of(listOf(night), emptyList(), resting, emptyList(), cest).single().restingHr).isNull()
    }

    @Test
    fun `too few HRV values give no nightly HRV and sleeps without stages count whole`() {
        val night = sleep("n", "2026-10-19T21:00:00Z", "2026-10-20T05:00:00Z")

        val result = Nights.of(listOf(night), hrv("2026-10-19T22:00:00Z", 50.0, 60.0), emptyList(), emptyList(), cest).single()

        assertThat(result.hrvMs).isNull()
        assertThat(result.asleepMillis).isEqualTo(8 * 3_600_000L)
    }

    @Test
    fun `naps alone make no night and nights come out oldest first`() {
        val nights = Nights.of(
            listOf(
                sleep("b", "2026-10-19T21:00:00Z", "2026-10-20T05:00:00Z"),
                sleep("nap", "2026-10-18T12:00:00Z", "2026-10-18T13:30:00Z"),
                sleep("a", "2026-10-17T21:00:00Z", "2026-10-18T05:00:00Z"),
            ),
            emptyList(),
            emptyList(),
            emptyList(),
            cest,
        )

        assertThat(nights.map { it.date }).containsExactly(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 20))
    }

    @Test
    fun `the median of an even count is the mean of the middle pair`() {
        assertThat(Nights.median(listOf(63.0, 64.1, 60.0, 70.0))).isEqualTo(63.55)
        assertThat(Nights.median(emptyList())).isNull()
    }
}
