package app.pacinglab.core.analytics.load

import app.pacinglab.core.analytics.zones.MaxHeartRateEstimate
import app.pacinglab.core.analytics.zones.ThresholdEffort
import app.pacinglab.core.analytics.zones.ThresholdEstimates
import app.pacinglab.core.analytics.zones.ThresholdHeartRateEstimate
import app.pacinglab.core.analytics.zones.ZoneDistribution
import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.PhysiologyHistory
import app.pacinglab.core.model.RecordingMethod
import app.pacinglab.core.model.Sport
import app.pacinglab.core.model.Workout
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * A workout's share of training load.
 *
 * @property scores null when the workout has no heart rate or no resting heart rate is known for its date.
 * @property intensity time in the three polarized zones; null without heart rate anchors for the date.
 */
data class WorkoutLoad(
    val workoutId: String,
    val date: LocalDate,
    val durationMillis: Long,
    val scores: LoadScores?,
    val intensity: ZoneDistribution? = null,
)

/**
 * One calendar week (Monday to Sunday) of training.
 *
 * @property intensityMillis time below VT1, between VT1 and VT2 and above VT2.
 * @property strain Foster's monotony and strain; null for a week without load.
 */
data class WeekSummary(
    val start: LocalDate,
    val load: Double,
    val durationMillis: Long,
    val workouts: Int,
    val intensityMillis: LongArray,
    val strain: WeekStrain?,
) {
    override fun equals(other: Any?): Boolean = other is WeekSummary &&
        start == other.start &&
        load == other.load &&
        durationMillis == other.durationMillis &&
        workouts == other.workouts &&
        intensityMillis.contentEquals(other.intensityMillis) &&
        strain == other.strain

    override fun hashCode(): Int = start.hashCode()
}

object TrainingLoads {
    /**
     * Recordings longer than this were left running: the owner's watch has kept an interval workout
     * going for 34 to 89 hours, while real sessions last at most a few hours. They count for nothing.
     */
    val LONGEST_WORKOUT: Duration = Duration.ofHours(12)

    /**
     * Whether a workout is training. Walks and rides that an app detected by itself are everyday
     * movement (commutes, errands) and recorded only now and then, so they would add noise rather than
     * load; a detected run is kept, since it is usually a run the athlete forgot to start on the watch.
     */
    fun counts(workout: Workout): Boolean = Duration.between(workout.start, workout.end) <= LONGEST_WORKOUT &&
        (workout.recordingMethod != RecordingMethod.AUTOMATIC || workout.sport == Sport.RUN)

    /** The day a workout belongs to: the date it started where it was recorded. */
    fun dateOf(workout: Workout, fallbackZone: ZoneId): LocalDate = workout.start.atZone(workout.zoneOffset ?: fallbackZone).toLocalDate()

    /**
     * The [count] weeks up to and including the one holding [until], newest first. Weeks start on
     * Monday; weeks without workouts are included with zeros.
     */
    fun weeks(loads: List<WorkoutLoad>, until: LocalDate, count: Int): List<WeekSummary> {
        val daily = daily(loads)
        val lastMonday = until.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return List(count) { i ->
            val start = lastMonday.minusWeeks(i.toLong())
            val inWeek = loads.filter { it.date >= start && it.date < start.plusWeeks(1) }
            val intensity = LongArray(3)
            for (load in inWeek) {
                val zones = load.intensity ?: continue
                for (z in 0 until minOf(3, zones.millis.size)) intensity[z] += zones.millis[z]
            }
            val days = DoubleArray(7) { daily[start.plusDays(it.toLong())] ?: 0.0 }
            WeekSummary(
                start = start,
                load = days.sum(),
                durationMillis = inWeek.sumOf { it.durationMillis },
                workouts = inWeek.size,
                intensityMillis = intensity,
                strain = WeekStrain.of(days),
            )
        }
    }

    /** Stress scores added up per day; days with only unscored workouts are left out. */
    fun daily(loads: List<WorkoutLoad>): Map<LocalDate, Double> = loads
        .filter { it.scores != null }
        .groupBy { it.date }
        .mapValues { (_, day) -> day.sumOf { it.scores!!.stressScore } }
}

/**
 * Heart rate anchors for any day: the athlete's own values in force on that day, and PacingLab's own
 * for every value left empty: maximal heart rate estimated from workouts ([estimatedMax]), threshold
 * from the hardest half hour of running ([ThresholdHeartRateEstimate]) or else 90 % of the maximum,
 * resting heart rate measured during sleep.
 *
 * @param measuredResting nightly resting heart rate by the date of the morning, any order.
 * @param thresholdEfforts the hardest continuous half hour of each run, any order.
 */
class AnchorTimeline(
    private val history: PhysiologyHistory,
    measuredResting: Map<LocalDate, Double>,
    val estimatedMax: MaxHeartRateEstimate? = null,
    private val thresholdEfforts: List<ThresholdEffort> = emptyList(),
) {
    private val resting = measuredResting.toSortedMap()

    fun at(date: LocalDate): HeartRateAnchors? {
        val entry = history.at(date)
        val max = entry?.maxHr ?: estimatedMax?.bpm ?: return null
        // A threshold set with a higher maximum in mind can end up above an estimated one; the estimate then wins.
        val threshold = entry?.thresholdHr?.takeIf { it < max }
            ?: ThresholdHeartRateEstimate.of(thresholdEfforts, max, date)?.bpm
            ?: ThresholdEstimates.thresholdFromMax(max)
        val restingHr = (entry?.restingHr ?: measuredRestingAt(date))
            ?.takeIf { it in HeartRateAnchors.RESTING_HR_RANGE && it < threshold }
        return HeartRateAnchors(maxHr = max, thresholdHr = threshold, restingHr = restingHr)
    }

    /** The threshold PacingLab found in the runs up to [date], whether or not the athlete set one; null without a hard enough run. */
    fun estimatedThresholdAt(date: LocalDate): ThresholdHeartRateEstimate? {
        val max = history.at(date)?.maxHr ?: estimatedMax?.bpm ?: return null
        return ThresholdHeartRateEstimate.of(thresholdEfforts, max, date)
    }

    /**
     * Median of the measured values in the [BASELINE_DAYS] days up to [date]. Without any there, the
     * nearest [BASELINE_DAYS] days with measurements stand in: resting heart rate changes slowly, and
     * workouts from before the watch was worn at night still need a value. Null without measurements.
     */
    fun measuredRestingAt(date: LocalDate): Int? {
        if (resting.isEmpty()) return null
        val window = resting.subMap(date.minusDays(BASELINE_DAYS - 1L), date.plusDays(1)).values.ifEmpty {
            val before = resting.headMap(date).keys.lastOrNull()
            val after = resting.tailMap(date).keys.firstOrNull()
            val nearest = listOfNotNull(before, after).minBy { abs(ChronoUnit.DAYS.between(it, date)) }
            if (nearest < date) {
                resting.subMap(nearest.minusDays(BASELINE_DAYS - 1L), nearest.plusDays(1)).values
            } else {
                resting.subMap(nearest, nearest.plusDays(BASELINE_DAYS.toLong())).values
            }
        }
        val values = window.sorted()
        val middle = values.size / 2
        val median = if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) / 2
        return Math.round(median).toInt()
    }

    companion object {
        const val BASELINE_DAYS = 30
        val EMPTY = AnchorTimeline(PhysiologyHistory.EMPTY, emptyMap())
    }
}
