package app.pacinglab.core.database.codec

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.Lap
import app.pacinglab.core.model.Route
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Segment
import app.pacinglab.core.model.SegmentKind
import app.pacinglab.core.model.SleepStage
import app.pacinglab.core.model.SleepStageType
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Compact binary encoding for workout streams, stored as BLOBs.
 *
 * Every blob starts with a format version byte. Timestamps are stored as the first value plus
 * deltas in their greatest common unit (Fitbit timestamps fall on whole seconds), values as
 * delta-encoded zigzag varints, so a 1 Hz heart rate stream costs about two bytes per sample.
 * Values are quantised to a
 * precision well beyond what the sensors deliver:
 * - speed: 1 mm/s
 * - latitude/longitude: 1e-7 degrees (about 1 cm)
 * - altitude and GPS accuracy: 1 cm
 * - lap length: 1 mm
 *
 * Changing an existing layout requires a new version number and a decoder for the old one.
 */
object StreamCodec {
    private const val VERSION = 1

    fun encodeHeartRate(series: HeartRateSeries): ByteArray = write {
        count(series.size)
        times(series.times)
        deltas(LongArray(series.size) { series.bpm[it].toLong() })
    }

    fun decodeHeartRate(data: ByteArray): HeartRateSeries = read(data) {
        val n = count()
        val times = times(n)
        val bpm = deltas(n)
        HeartRateSeries(times, IntArray(n) { bpm[it].toInt() })
    }

    fun encodeSamples(series: SampleSeries, scale: Double): ByteArray = write {
        require(series.values.all { it.isFinite() }) { "Sample values must be finite" }
        count(series.size)
        times(series.times)
        deltas(LongArray(series.size) { (series.values[it] * scale).roundToLong() })
    }

    fun decodeSamples(data: ByteArray, scale: Double): SampleSeries = read(data) {
        val n = count()
        val times = times(n)
        val values = deltas(n)
        SampleSeries(times, DoubleArray(n) { values[it] / scale })
    }

    fun encodeRoute(route: Route): ByteArray = write {
        val n = route.size
        count(n)
        times(route.times)
        deltas(LongArray(n) { (route.latitudes[it] * DEGREE_SCALE).roundToLong() })
        deltas(LongArray(n) { (route.longitudes[it] * DEGREE_SCALE).roundToLong() })
        optionalColumn(route.altitudes, CENTIMETRES)
        optionalColumn(route.accuracies, CENTIMETRES)
    }

    fun decodeRoute(data: ByteArray): Route = read(data) {
        val n = count()
        val times = times(n)
        val lat = deltas(n)
        val lon = deltas(n)
        val altitudes = optionalColumn(n, CENTIMETRES)
        val accuracies = optionalColumn(n, CENTIMETRES)
        Route(
            times = times,
            latitudes = DoubleArray(n) { lat[it] / DEGREE_SCALE },
            longitudes = DoubleArray(n) { lon[it] / DEGREE_SCALE },
            altitudes = altitudes,
            accuracies = accuracies,
        )
    }

    fun encodeLaps(laps: List<Lap>): ByteArray = write {
        count(laps.size)
        for (lap in laps) {
            writer.signedVarLong(lap.start.toEpochMilli())
            writer.signedVarLong(lap.end.toEpochMilli() - lap.start.toEpochMilli())
            val length = lap.lengthMeters
            if (length == null || !length.isFinite()) {
                writer.byte(0)
            } else {
                writer.byte(1)
                writer.signedVarLong((length * 1_000).roundToLong())
            }
        }
    }

    fun decodeLaps(data: ByteArray): List<Lap> = read(data) {
        List(count()) {
            val start = reader.signedVarLong()
            val end = start + reader.signedVarLong()
            val length = if (reader.byte() == 1) reader.signedVarLong() / 1_000.0 else null
            Lap(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), length)
        }
    }

    fun encodeSegments(segments: List<Segment>): ByteArray = write {
        count(segments.size)
        for (segment in segments) {
            writer.signedVarLong(segment.start.toEpochMilli())
            writer.signedVarLong(segment.end.toEpochMilli() - segment.start.toEpochMilli())
            writer.varLong(segmentKindCode(segment.kind).toLong())
            writer.signedVarLong(segment.sourceType.toLong())
            writer.signedVarLong(segment.repetitions.toLong())
        }
    }

    fun decodeSegments(data: ByteArray): List<Segment> = read(data) {
        List(count()) {
            val start = reader.signedVarLong()
            val end = start + reader.signedVarLong()
            Segment(
                start = Instant.ofEpochMilli(start),
                end = Instant.ofEpochMilli(end),
                kind = segmentKindFromCode(reader.varLong().toInt()),
                sourceType = reader.signedVarLong().toInt(),
                repetitions = reader.signedVarLong().toInt(),
            )
        }
    }

    fun encodeSleepStages(stages: List<SleepStage>): ByteArray = write {
        count(stages.size)
        for (stage in stages) {
            writer.signedVarLong(stage.start.toEpochMilli())
            writer.signedVarLong(stage.end.toEpochMilli() - stage.start.toEpochMilli())
            writer.varLong(sleepStageCode(stage.type).toLong())
        }
    }

    fun decodeSleepStages(data: ByteArray): List<SleepStage> = read(data) {
        List(count()) {
            val start = reader.signedVarLong()
            val end = start + reader.signedVarLong()
            SleepStage(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end), sleepStageFromCode(reader.varLong().toInt()))
        }
    }

    const val SPEED_SCALE = 1_000.0
    private const val DEGREE_SCALE = 1e7
    private const val CENTIMETRES = 100.0

    // Stable codes, independent of enum declaration order.
    private fun segmentKindCode(kind: SegmentKind) = when (kind) {
        SegmentKind.OTHER -> 0
        SegmentKind.RUNNING -> 1
        SegmentKind.PAUSE -> 2
    }

    private fun segmentKindFromCode(code: Int) = when (code) {
        1 -> SegmentKind.RUNNING
        2 -> SegmentKind.PAUSE
        else -> SegmentKind.OTHER
    }

    private fun sleepStageCode(type: SleepStageType) = when (type) {
        SleepStageType.UNKNOWN -> 0
        SleepStageType.AWAKE -> 1
        SleepStageType.LIGHT -> 2
        SleepStageType.DEEP -> 3
        SleepStageType.REM -> 4
        SleepStageType.ASLEEP_UNSPECIFIED -> 5
        SleepStageType.OUT_OF_BED -> 6
    }

    private fun sleepStageFromCode(code: Int) = when (code) {
        1 -> SleepStageType.AWAKE
        2 -> SleepStageType.LIGHT
        3 -> SleepStageType.DEEP
        4 -> SleepStageType.REM
        5 -> SleepStageType.ASLEEP_UNSPECIFIED
        6 -> SleepStageType.OUT_OF_BED
        else -> SleepStageType.UNKNOWN
    }

    private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

    private inline fun write(block: EncodeScope.() -> Unit): ByteArray {
        val scope = EncodeScope(ByteWriter())
        scope.writer.byte(VERSION)
        scope.block()
        return scope.writer.toByteArray()
    }

    private inline fun <T> read(data: ByteArray, block: DecodeScope.() -> T): T {
        val scope = DecodeScope(ByteReader(data))
        val version = scope.reader.byte()
        if (version != VERSION) throw CorruptStreamException("Unsupported stream format version $version")
        val result = scope.block()
        if (scope.reader.hasRemaining) throw CorruptStreamException("Trailing bytes after stream")
        return result
    }

    private class EncodeScope(val writer: ByteWriter) {
        fun count(n: Int) = writer.varLong(n.toLong())

        /** Strictly increasing timestamps: first value, common unit of the gaps, gaps in that unit. */
        fun times(times: LongArray) {
            if (times.isEmpty()) return
            writer.signedVarLong(times[0])
            if (times.size == 1) return
            var unit = 0L
            for (i in 1 until times.size) unit = gcd(unit, times[i] - times[i - 1])
            writer.varLong(unit)
            for (i in 1 until times.size) writer.varLong((times[i] - times[i - 1]) / unit)
        }

        fun deltas(values: LongArray) {
            var previous = 0L
            for (v in values) {
                writer.signedVarLong(v - previous)
                previous = v
            }
        }

        /** Presence flag, then a bitmap of present entries and the present values as deltas. */
        fun optionalColumn(values: DoubleArray, scale: Double) {
            val present = BooleanArray(values.size) { values[it].isFinite() }
            if (present.none { it }) {
                writer.byte(0)
                return
            }
            writer.byte(1)
            var bits = 0
            for (i in present.indices) {
                if (present[i]) bits = bits or (1 shl (i % 8))
                if (i % 8 == 7 || i == present.lastIndex) {
                    writer.byte(bits)
                    bits = 0
                }
            }
            deltas(values.filter { it.isFinite() }.map { (it * scale).roundToLong() }.toLongArray())
        }
    }

    private class DecodeScope(val reader: ByteReader) {
        fun count(): Int = reader.count()

        fun times(n: Int): LongArray {
            val out = LongArray(n)
            if (n == 0) return out
            out[0] = reader.signedVarLong()
            if (n == 1) return out
            val unit = reader.varLong()
            if (unit <= 0) throw CorruptStreamException("Invalid time unit $unit")
            for (i in 1 until n) out[i] = out[i - 1] + reader.varLong() * unit
            return out
        }

        fun deltas(n: Int): LongArray {
            val out = LongArray(n)
            var previous = 0L
            for (i in 0 until n) {
                previous += reader.signedVarLong()
                out[i] = previous
            }
            return out
        }

        fun optionalColumn(n: Int, scale: Double): DoubleArray {
            val out = DoubleArray(n) { Double.NaN }
            if (reader.byte() == 0) return out
            val present = BooleanArray(n)
            var bits = 0
            for (i in 0 until n) {
                if (i % 8 == 0) bits = reader.byte()
                present[i] = bits and (1 shl (i % 8)) != 0
            }
            val values = deltas(present.count { it })
            var j = 0
            for (i in 0 until n) if (present[i]) out[i] = values[j++] / scale
            return out
        }
    }
}
