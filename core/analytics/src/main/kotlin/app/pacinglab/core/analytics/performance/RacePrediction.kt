package app.pacinglab.core.analytics.performance

import kotlin.math.exp

/** Predicted finishing times for one distance, seconds; null where a method has nothing to say. */
data class RacePrediction(val distance: RaceDistance, val fromVo2Max: Double?, val fromCriticalSpeed: Double?)

/**
 * Race times from two independent models.
 *
 * From VO2max, Daniels and Gilbert (1979): the oxygen cost of running at v metres per minute is
 * -4.60 + 0.182258 v + 0.000104 v^2 ml/kg/min, and the share of VO2max that can be held for t minutes
 * is 0.8 + 0.1894393 e^(-0.012778 t) + 0.2989558 e^(-0.1932605 t). The predicted time is the t at
 * which the two meet. Daniels' tables assume training suited to the distance; a marathon needs long
 * runs that a VO2max value cannot show.
 *
 * From critical speed: (distance - D') / CS, only where the race would last 2 to 30 minutes, the range
 * in which the model describes performance.
 */
object RacePredictions {
    val DISTANCES = listOf(RaceDistance.K5, RaceDistance.K10, RaceDistance.HALF_MARATHON, RaceDistance.MARATHON)

    fun of(vo2max: Double?, criticalSpeed: CriticalSpeedFit?): List<RacePrediction> = DISTANCES.map { distance ->
        RacePrediction(distance, vo2max?.let { secondsFromVo2Max(it, distance.meters) }, criticalSpeed?.secondsFor(distance.meters))
    }

    /** Daniels-Gilbert finishing time for [meters] at [vo2max] ml/kg/min, seconds. */
    fun secondsFromVo2Max(vo2max: Double, meters: Double): Double? {
        if (vo2max !in VO2MAX_RANGE) return null
        // Too short a time needs more oxygen than VO2max allows, too long a time less; bisect between.
        var fast = 1.0
        var slow = 1_000.0
        if (shortfall(vo2max, meters, slow) > 0) return null
        repeat(ITERATIONS) {
            val middle = (fast + slow) / 2
            if (shortfall(vo2max, meters, middle) > 0) fast = middle else slow = middle
        }
        return (fast + slow) / 2 * 60
    }

    /** Oxygen cost of running [meters] in [minutes] minus what can be sustained for that long. */
    private fun shortfall(vo2max: Double, meters: Double, minutes: Double): Double {
        val v = meters / minutes
        val cost = -4.60 + 0.182258 * v + 0.000104 * v * v
        val share = 0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes)
        return cost - vo2max * share
    }

    private val VO2MAX_RANGE = 20.0..90.0
    private const val ITERATIONS = 60
}
