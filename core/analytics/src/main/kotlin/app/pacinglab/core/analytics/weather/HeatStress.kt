package app.pacinglab.core.analytics.weather

import kotlin.math.exp

/** Heat stress bands of the American College of Sports Medicine for exercise (Roberts 2021). */
enum class HeatStressLevel {
    /** Below 18 °C WBGT: heat does not limit endurance exercise much. */
    LOW,

    /** 18 to 23 °C: heart rate runs higher and drifts more; slower pace for the same effort. */
    MODERATE,

    /** 23 to 28 °C: high risk of heat illness for hard efforts. */
    HIGH,

    /** 28 °C and above: ACSM advises cancelling hard events. */
    EXTREME,
}

/**
 * Heat stress from temperature and humidity: the wet-bulb globe temperature (WBGT) in the shade, by the
 * Australian Bureau of Meteorology's approximation, 0.567 T + 0.393 e + 3.94, where e is the water
 * vapour pressure in hPa. Sun adds to it, which this leaves out. Heat raises heart rate at a given pace
 * and makes it drift upwards through a run (cardiovascular drift; González-Alonso 1999), so heart rate
 * based measures of a hot run (VO2max estimate, efficiency, decoupling) read worse than the athlete's
 * fitness.
 */
object HeatStress {
    fun wbgt(temperature: Double, humidityPercent: Double): Double {
        val vapourPressure = humidityPercent / 100 * 6.105 * exp(17.27 * temperature / (237.7 + temperature))
        return 0.567 * temperature + 0.393 * vapourPressure + 3.94
    }

    fun level(wbgt: Double): HeatStressLevel = when {
        wbgt < 18 -> HeatStressLevel.LOW
        wbgt < 23 -> HeatStressLevel.MODERATE
        wbgt < 28 -> HeatStressLevel.HIGH
        else -> HeatStressLevel.EXTREME
    }
}

/**
 * How much heat slows running at a given effort, for adjusting heart rate based estimates to cool
 * conditions. Across 1,258 elite races, performance was best between 7.5 and 15 °C WBGT and fell by 0.3
 * to 0.4 % for every degree above (Mantzios 2022). A run at the same heart rate in the heat therefore
 * stands for a speed about that much higher in cool conditions; estimates built from speed at heart rate
 * (VO2max per run, aerobic efficiency) are scaled by [speedFactor]. The races were run all out, so for
 * steady easy and moderate running this is an assumption; it agrees in size with heart rate rising a few
 * beats a minute at a given pace on warm days. Cold is left alone: it hardly raises heart rate.
 */
object HeatAdjustment {
    /** Upper end of the WBGT range with the best performances, °C. */
    const val OPTIMUM_UPPER = 15.0

    /** Share of speed lost per degree of WBGT above [OPTIMUM_UPPER]: the middle of 0.3 to 0.4 %. */
    const val PER_DEGREE = 0.0035

    /** The factor a speed run at [wbgt] is multiplied by to stand for cool conditions; 1 up to 15 °C. */
    fun speedFactor(wbgt: Double): Double = 1 + PER_DEGREE * maxOf(0.0, wbgt - OPTIMUM_UPPER)

    /**
     * A VO2max estimated from speed at heart rate, adjusted to cool conditions: the oxygen cost above rest
     * (3.5 ml/kg/min) grows with speed, so that part is scaled by [speedFactor].
     */
    fun vo2max(estimate: Double, wbgt: Double): Double = REST + speedFactor(wbgt) * (estimate - REST)

    private const val REST = 3.5
}
