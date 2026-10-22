package app.pacinglab.core.analytics.workout

import app.pacinglab.core.analytics.intervals.SprintDetection
import app.pacinglab.core.model.SampleSeries
import app.pacinglab.core.model.Sport

/**
 * Decides what a workout was. Most source activity types map directly, but generic types need the
 * data: Fitbit records its "Interval workout" mode as boot camp, and for this athlete those are
 * running intervals, which the speed stream reveals. Sprint sessions run for well under the
 * [MIN_RUNNING_MILLIS] of a run and are recognised by their sprints instead ([SprintDetection]).
 */
object SportClassifier {
    private val direct = mapOf(
        "running" to Sport.RUN,
        "running_treadmill" to Sport.RUN,
        "walking" to Sport.WALK,
        "hiking" to Sport.WALK,
        "biking" to Sport.RIDE,
        "biking_stationary" to Sport.RIDE,
        "strength_training" to Sport.STRENGTH,
        "weightlifting" to Sport.STRENGTH,
        "calisthenics" to Sport.STRENGTH,
    )

    /** Generic activity types whose sport is inferred from speed. */
    private val inferable = setOf("boot_camp", "high_intensity_interval_training", "other_workout", "exercise_class")

    /** Below this the athlete is standing or walking slowly. */
    private const val MOVING_SPEED = 0.8

    /** About 7:35 min/km: slower than this counts as walking. */
    private const val RUNNING_SPEED = 2.2

    /** Faster than this as a typical moving speed means cycling, not running. */
    private const val CYCLING_SPEED = 7.0

    /** Running must make up at least this share of moving time... */
    private const val MIN_RUNNING_SHARE = 0.3

    /** ...and last at least this long in total. */
    private const val MIN_RUNNING_MILLIS = 3 * 60_000L

    /**
     * Average speed, from the recording app's distance, above which a generic workout counts as a run
     * when there is no speed stream (Fitbit only writes speed since August 2026). Interval sessions
     * with walking recoveries average 2.1-2.7 m/s; gym sessions recorded in interval mode stay below
     * 0.3 m/s.
     */
    private const val RUNNING_AVERAGE_SPEED = 1.8

    /** Below this distance there is too little movement to call it a run. */
    private const val MIN_RUNNING_DISTANCE = 1_000.0

    fun classify(activity: String, speed: SampleSeries, distanceMeters: Double? = null, durationMillis: Long = 0): Sport {
        direct[activity]?.let { return it }
        if (activity !in inferable) return Sport.OTHER
        if (speed.size < 2) return classifyByDistance(distanceMeters, durationMillis)

        val clean = SpeedCleaning.dropImplausible(speed, SpeedCleaning.maxPlausibleSpeed(Sport.RIDE))
        var movingMillis = 0L
        var runningMillis = 0L
        val movingSpeeds = ArrayList<Double>()
        for (i in 0 until clean.size - 1) {
            val dt = minOf(clean.times[i + 1] - clean.times[i], 5_000L)
            val v = clean.values[i]
            if (v >= MOVING_SPEED) {
                movingMillis += dt
                movingSpeeds += v
                if (v >= RUNNING_SPEED) runningMillis += dt
            }
        }
        if (movingMillis == 0L) return Sport.OTHER
        movingSpeeds.sort()
        val medianMoving = movingSpeeds[movingSpeeds.size / 2]
        return when {
            medianMoving > CYCLING_SPEED -> Sport.RIDE
            runningMillis >= MIN_RUNNING_MILLIS && runningMillis.toDouble() / movingMillis >= MIN_RUNNING_SHARE -> Sport.RUN
            isSprintSession(speed) -> Sport.RUN
            else -> Sport.OTHER
        }
    }

    private fun isSprintSession(speed: SampleSeries): Boolean {
        val running = SpeedCleaning.dropImplausible(speed, SpeedCleaning.maxPlausibleSpeed(Sport.RUN))
        if (running.size < 2) return false
        return SprintDetection.detect(running, running.times.first(), running.times.last() + 1_000) != null
    }

    private fun classifyByDistance(distanceMeters: Double?, durationMillis: Long): Sport {
        if (distanceMeters == null || distanceMeters < MIN_RUNNING_DISTANCE || durationMillis <= 0) return Sport.OTHER
        val average = distanceMeters / (durationMillis / 1000.0)
        return when {
            average > CYCLING_SPEED -> Sport.RIDE
            average >= RUNNING_AVERAGE_SPEED -> Sport.RUN
            else -> Sport.OTHER
        }
    }
}
