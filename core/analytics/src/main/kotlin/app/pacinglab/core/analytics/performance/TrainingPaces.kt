package app.pacinglab.core.analytics.performance

import kotlin.math.sqrt

/**
 * Daniels' five training intensities as speeds in m/s, faster speeds being harder.
 *
 * @property easy easy and long runs: a range from its slow to its fast end.
 * @property marathon marathon race pace.
 * @property threshold threshold ("T") pace: comfortably hard, about an hour's race pace.
 * @property interval interval ("I") pace: work at VO2max, about 10 to 15 minutes' race pace.
 * @property repetition repetition ("R") pace: short fast reps for speed and economy, about mile race pace.
 */
data class TrainingPaces(
    val vo2max: Double,
    val easy: ClosedFloatingPointRange<Double>,
    val marathon: Double,
    val threshold: Double,
    val interval: Double,
    val repetition: Double,
)

/**
 * Training paces from VO2max after Daniels (Daniels' Running Formula): each intensity is a share of
 * VO2max turned into a speed with the Daniels-Gilbert oxygen cost of running,
 * VO2 = -4.60 + 0.182258 v + 0.000104 v^2 (v in m/min). Easy pace spans Daniels' 59 to 74 % of VO2max;
 * threshold, interval and repetition use 88, 97.5 and 105 %, which reproduce his tables (at VO2max 50:
 * threshold 4:15, interval 3:55 and repetition 3:42 per km, within two seconds). Marathon pace is the
 * predicted marathon time's pace, as in the tables.
 */
object TrainingPaceCalculator {
    private val EASY = 0.59..0.74
    private const val THRESHOLD = 0.88
    private const val INTERVAL = 0.975
    private const val REPETITION = 1.05
    private const val MARATHON_METERS = 42_195.0

    /** The paces for [vo2max] in ml/kg/min; null outside the range the race model covers. */
    fun of(vo2max: Double): TrainingPaces? {
        val marathon = RacePredictions.secondsFromVo2Max(vo2max, MARATHON_METERS) ?: return null
        return TrainingPaces(
            vo2max = vo2max,
            easy = speedAt(vo2max * EASY.start)..speedAt(vo2max * EASY.endInclusive),
            marathon = MARATHON_METERS / marathon,
            threshold = speedAt(vo2max * THRESHOLD),
            interval = speedAt(vo2max * INTERVAL),
            repetition = speedAt(vo2max * REPETITION),
        )
    }

    /** The running speed in m/s whose oxygen cost is [vo2] ml/kg/min: the positive root of the cost equation. */
    internal fun speedAt(vo2: Double): Double {
        val a = 0.000104
        val b = 0.182258
        val c = -4.60 - vo2
        val metersPerMinute = (-b + sqrt(b * b - 4 * a * c)) / (2 * a)
        return metersPerMinute / 60
    }
}
