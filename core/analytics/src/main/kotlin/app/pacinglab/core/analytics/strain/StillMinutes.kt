package app.pacinglab.core.analytics.strain

import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.SleepSession
import app.pacinglab.core.model.StepCount
import app.pacinglab.core.model.StillHeartRate
import app.pacinglab.core.model.StillHeartRate.Companion.HOURS
import app.pacinglab.core.model.Workout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which minutes of a day are still and awake, the raw material of [DaytimeStrain], summed into a
 * [StillHeartRate].
 *
 * A minute counts as still when the watch has at least [MIN_SAMPLES] heart rate samples in it, no device
 * counted steps in it or in the [SETTLE_MINUTES] before (heart rate takes a couple of minutes to settle after
 * walking), it lies outside every sleep record and outside workouts and the time after them
 * ([excludedSpans]), and its heart rate stays at or below [MAX_STILL_BPM]: higher without steps is
 * exercise the watch did not record, such as cycling.
 */
object StillMinutes {
    const val MIN_SAMPLES = 3
    const val SETTLE_MINUTES = 5
    const val MAX_STILL_BPM = 130

    /** Time after a workout the athlete recorded, while heart rate is still coming down from it. */
    val AFTER_TRAINING: Duration = Duration.ofMinutes(60)

    /** Time after a walk or ride a phone or watch detected by itself, usually a commute. */
    val AFTER_DETECTED: Duration = Duration.ofMinutes(15)

    /** Time before a workout, for warming up and getting ready. */
    val BEFORE_WORKOUT: Duration = Duration.ofMinutes(5)

    private const val MINUTE_MILLIS = 60_000L

    /**
     * The still heart rate of [date] where it was spent ([zone]), up to [until] for a day still going on.
     *
     * @param heartRate the watch's heart rate over the day.
     * @param steps step counts of every device over the day and the minutes before it.
     * @param excluded spans that are never still, from [excludedSpans].
     */
    fun summarize(
        date: LocalDate,
        zone: ZoneId,
        heartRate: HeartRateSeries,
        steps: List<StepCount>,
        excluded: List<ClosedRange<Instant>>,
        until: Instant? = null,
    ): StillHeartRate {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().let { if (until != null && until < it) until else it }
        val dayStart = start.toEpochMilli()
        val count = ((end.toEpochMilli() - dayStart) / MINUTE_MILLIS).toInt().coerceAtLeast(0)
        val sums = DoubleArray(count)
        val samples = IntArray(count)
        for (i in 0 until heartRate.size) {
            val m = Math.floorDiv(heartRate.times[i] - dayStart, MINUTE_MILLIS)
            if (m < 0 || m >= count) continue
            sums[m.toInt()] += heartRate.bpm[i].toDouble()
            samples[m.toInt()]++
        }
        // Minutes with steps, from the minutes before the day too, so that the first minutes can settle.
        val stepped = BooleanArray(count + SETTLE_MINUTES)
        for (step in steps) {
            if (step.count <= 0) continue
            val first = Math.floorDiv(step.start.toEpochMilli() - dayStart, MINUTE_MILLIS) + SETTLE_MINUTES
            val last = Math.floorDiv(step.end.toEpochMilli() - 1 - dayStart, MINUTE_MILLIS) + SETTLE_MINUTES
            for (m in maxOf(first, 0L)..minOf(last, stepped.size - 1L)) stepped[m.toInt()] = true
        }
        val blocked = BooleanArray(count)
        for (span in excluded) {
            val first = Math.floorDiv(span.start.toEpochMilli() - dayStart, MINUTE_MILLIS)
            val last = Math.floorDiv(span.endInclusive.toEpochMilli() - 1 - dayStart, MINUTE_MILLIS)
            for (m in maxOf(first, 0L)..minOf(last, count - 1L)) blocked[m.toInt()] = true
        }
        val minutes = IntArray(HOURS)
        val hourSums = DoubleArray(HOURS)
        for (m in 0 until count) {
            if (samples[m] < MIN_SAMPLES || blocked[m]) continue
            if ((m..m + SETTLE_MINUTES).any { stepped[it] }) continue
            val bpm = sums[m] / samples[m]
            if (bpm > MAX_STILL_BPM) continue
            val hour = Instant.ofEpochMilli(dayStart + m * MINUTE_MILLIS).atZone(zone).hour
            minutes[hour]++
            hourSums[hour] += bpm
        }
        return StillHeartRate(date, minutes, hourSums)
    }

    /**
     * The spans no minute of which counts as still: every sleep record whole (lying in bed, asleep or
     * not), and every workout from [BEFORE_WORKOUT] before it to [AFTER_TRAINING] after it, or
     * [AFTER_DETECTED] after a walk or ride an app detected by itself.
     */
    fun excludedSpans(sleeps: List<SleepSession>, workouts: List<Workout>): List<ClosedRange<Instant>> =
        sleeps.map { it.start..it.end } + workouts.map { workout ->
            val after = if (workout.recordingMethod == RecordingMethod.AUTOMATIC) AFTER_DETECTED else AFTER_TRAINING
            workout.start.minus(BEFORE_WORKOUT)..workout.end.plus(after)
        }
}
