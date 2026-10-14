package app.pacinglab.core.database.codec

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Lap
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Segment
import app.pacinglab.core.model.SegmentKind
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

class StreamCodecTest {
    @Test
    fun `heart rate round trips exactly`() = runTest {
        checkAll(Arb.list(Arb.int(1..5_000), 0..500), Arb.long(0L..2_000_000_000_000L)) { steps, start ->
            val times = times(start, steps)
            val bpm = IntArray(times.size) { 40 + (it * 37) % 180 }
            val series = HeartRateSeries(times, bpm)

            assertThat(StreamCodec.decodeHeartRate(StreamCodec.encodeHeartRate(series))).isEqualTo(series)
        }
    }

    @Test
    fun `one hertz heart rate costs about two bytes per sample`() {
        val n = 3_600
        val series = HeartRateSeries(LongArray(n) { 1_791_825_365_000 + it * 1_000L }, IntArray(n) { 150 + (it % 7) - 3 })

        assertThat(StreamCodec.encodeHeartRate(series).size).isLessThan(2 * n + 16)
    }

    @Test
    fun `speed round trips to the millimetre per second`() = runTest {
        checkAll(Arb.list(Arb.double(0.0, 30.0, includeNaNs = false), 0..300)) { values ->
            val series = SampleSeries(LongArray(values.size) { it * 1_000L }, values.toDoubleArray())

            val decoded = StreamCodec.decodeSamples(StreamCodec.encodeSamples(series, StreamCodec.SPEED_SCALE), StreamCodec.SPEED_SCALE)

            assertThat(decoded.times.contentEquals(series.times)).isTrue()
            assertThat(decoded.values.indices.all { abs(decoded.values[it] - values[it]) <= 0.0005 }).isTrue()
        }
    }

    @Test
    fun `route round trips including missing altitude and accuracy`() = runTest {
        checkAll(Arb.list(Arb.boolean(), 0..200), Arb.list(Arb.boolean(), 0..200)) { altitudeMask, accuracyMask ->
            val n = minOf(altitudeMask.size, accuracyMask.size)
            val route = Route(
                times = LongArray(n) { 1_000_000L + it * 1_000L },
                latitudes = DoubleArray(n) { 55.6761 + it * 1e-5 },
                longitudes = DoubleArray(n) { 12.5683 - it * 2e-5 },
                altitudes = DoubleArray(n) { if (altitudeMask[it]) 12.3 + it * 0.1 else Double.NaN },
                accuracies = DoubleArray(n) { if (accuracyMask[it]) 3.5 else Double.NaN },
            )

            val decoded = StreamCodec.decodeRoute(StreamCodec.encodeRoute(route))

            assertThat(decoded.times.contentEquals(route.times)).isTrue()
            for (i in 0 until n) {
                assertThat(abs(decoded.latitudes[i] - route.latitudes[i]) < 1e-7).isTrue()
                assertThat(abs(decoded.longitudes[i] - route.longitudes[i]) < 1e-7).isTrue()
                assertThat(decoded.altitudes[i].isNaN() == route.altitudes[i].isNaN()).isTrue()
                if (!route.altitudes[i].isNaN()) assertThat(abs(decoded.altitudes[i] - route.altitudes[i]) < 0.006).isTrue()
                assertThat(decoded.accuracies[i].isNaN() == route.accuracies[i].isNaN()).isTrue()
            }
        }
    }

    @Test
    fun `laps segments and sleep stages round trip`() {
        val t = Instant.parse("2026-10-08T17:34:00Z")
        val laps = listOf(Lap(t, t.plusSeconds(326), 1_000.0), Lap(t.plusSeconds(326), t.plusSeconds(365), null))
        val segments = listOf(Segment(t, t.plusSeconds(366), SegmentKind.RUNNING, 46, 0), Segment(t, t, SegmentKind.PAUSE, 39, 2))
        val stages = SleepStageType.entries.mapIndexed { i, type -> SleepStage(t.plusSeconds(i * 60L), t.plusSeconds(i * 60L + 59), type) }

        assertThat(StreamCodec.decodeLaps(StreamCodec.encodeLaps(laps))).isEqualTo(laps)
        assertThat(StreamCodec.decodeSegments(StreamCodec.encodeSegments(segments))).isEqualTo(segments)
        assertThat(StreamCodec.decodeSleepStages(StreamCodec.encodeSleepStages(stages))).isEqualTo(stages)
    }

    @Test
    fun `corrupt data is rejected rather than misread`() {
        val valid = StreamCodec.encodeHeartRate(HeartRateSeries.of(0L to 100, 1_000L to 101))

        assertFailure { StreamCodec.decodeHeartRate(valid.copyOf(valid.size - 1)) }.isInstanceOf<CorruptStreamException>()
        assertFailure { StreamCodec.decodeHeartRate(valid + byteArrayOf(0)) }.isInstanceOf<CorruptStreamException>()
        assertFailure { StreamCodec.decodeHeartRate(byteArrayOf(99) + valid.copyOfRange(1, valid.size)) }
            .isInstanceOf<CorruptStreamException>()
    }

    @Test
    fun `zigzag maps signed values to unsigned and back`() = runTest {
        checkAll(Arb.long()) { n -> assertThat(unZigZag(zigZag(n))).isEqualTo(n) }
    }

    private fun times(start: Long, steps: List<Int>): LongArray {
        val times = LongArray(steps.size + 1)
        times[0] = start
        for (i in steps.indices) times[i + 1] = times[i] + steps[i]
        return times
    }
}
