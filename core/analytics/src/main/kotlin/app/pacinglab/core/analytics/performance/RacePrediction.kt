package app.pacinglab.core.analytics.performance

import kotlin.math.exp

/**
 * Predicted finishing times for one distance, seconds; null where a method has nothing to say.
 *
 * @property fromVo2Max from VO2max, slowed where the marathon shape is short of what the distance asks for.
 * @property potential from VO2max alone: the time with training suited to the distance.
 */
data class RacePrediction(
    val distance: RaceDistance,
    val fromVo2Max: Double?,
    val fromCriticalSpeed: Double?,
    val potential: Double? = fromVo2Max,
)

/**
 * Race times from two independent models.
 *
 * From VO2max, Daniels and Gilbert (1979): the oxygen cost of running at v metres per minute is
 * -4.60 + 0.182258 v + 0.000104 v^2 ml/kg/min, and the share of VO2max that can be held for t minutes
 * is 0.8 + 0.1894393 e^(-0.012778 t) + 0.2989558 e^(-0.1932605 t). The predicted time is the t at
 * which the two meet. Daniels' tables assume training suited to the distance; a marathon needs long
 * runs that a VO2max value cannot show, so with a [MarathonShape] the VO2max is first scaled by
 * [MarathonShapes.factor] for the distance (Runalyze's adjustment).
 *
 * From critical speed: (distance - D') / CS, only where the race would last 2 to 30 minutes, the range
 * in which the model describes performance.
 */
object RacePredictions {
    val DISTANCES = listOf(RaceDistance.K5, RaceDistance.K10, RaceDistance.HALF_MARATHON, RaceDistance.MARATHON)

    fun of(vo2max: Double?, criticalSpeed: CriticalSpeedFit?, shape: MarathonShape? = null): List<RacePrediction> =
        DISTANCES.map { distance ->
            val potential = vo2max?.let { secondsFromVo2Max(it, distance.meters) }
            val shaped = if (vo2max != null && shape != null) {
                secondsFromVo2Max(vo2max * MarathonShapes.factor(distance.meters, shape.percent), distance.meters)
            } else {
                potential
            }
            RacePrediction(distance, shaped, criticalSpeed?.secondsFor(distance.meters), potential)
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
