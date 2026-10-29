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
