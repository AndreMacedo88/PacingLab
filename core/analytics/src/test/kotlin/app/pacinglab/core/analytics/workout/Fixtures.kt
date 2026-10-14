package app.pacinglab.core.analytics.workout

import app.pacinglab.core.model.DataSource
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import app.pacinglab.core.model.WorkoutSummary
import java.time.Duration
import java.time.Instant

internal val T0: Instant = Instant.parse("2026-10-08T17:34:00Z")

/** 1 Hz speed stream built from (seconds, m/s) blocks. */
internal fun speedBlocks(vararg blocks: Pair<Int, Double>): SampleSeries {
    val total = blocks.sumOf { it.first }
    val times = LongArray(total)
    val values = DoubleArray(total)
    var i = 0
    for ((seconds, speed) in blocks) {
        repeat(seconds) {
            times[i] = T0.toEpochMilli() + i * 1_000L
            values[i] = speed
            i++
        }
    }
    return SampleSeries(times, values)
}

internal fun workout(id: String, sport: Sport, startMinute: Long, minutes: Long): Workout {
    val start = T0.plus(Duration.ofMinutes(startMinute))
    return Workout(
        id = id,
        source = DataSource.HEALTH_CONNECT,
        sourceRecordId = id,
        origin = "com.fitbit.FitbitMobile",
        device = "Sense",
        activity = "running",
        title = null,
        start = start,
        end = start.plus(Duration.ofMinutes(minutes)),
        zoneOffset = null,
        lastModified = start,
        sport = sport,
        summary = WorkoutSummary(minutes * 60_000, null, null, null, hasRoute = false, hasSpeed = false),
        laps = emptyList(),
        segments = emptyList(),
    )
}
