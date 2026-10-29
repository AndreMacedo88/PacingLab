package app.pacinglab.core.analytics.performance

/**
 * Fitness age as NTNU's Cardiac Exercise Research Group defines it: the age at which a VO2max is the
 * average of healthy people of the same sex. The averages are the HUNT3 Fitness Study's measured VO2peak
 * per decade (Loe et al. 2013, PLoS ONE 8:e64319; as published by CERG), taken at the middle of each
 * decade and joined by straight lines. VO2max predicts mortality strongly (Kodama 2009, about 13 % lower
 * risk per 3.5 ml/kg/min), which is what makes the comparison meaningful.
 */
object FitnessAge {
    private val AGES = listOf(25.0, 35.0, 45.0, 55.0, 65.0, 75.0)
    private val MEN = listOf(54.0, 49.0, 47.0, 42.0, 39.0, 34.0)
    private val WOMEN = listOf(43.0, 40.0, 38.0, 34.0, 31.0, 27.0)

    /** Youngest and oldest ages reported; beyond the reference ages the nearest decade's slope is extended. */
    val RANGE = 20.0..90.0

    /** Average VO2max of healthy men, or women when [female], at [age]. */
    fun average(age: Double, female: Boolean = false): Double {
        val values = values(female)
        val i = segment(age)
        val t = (age - AGES[i]) / (AGES[i + 1] - AGES[i])
        return values[i] + t * (values[i + 1] - values[i])
    }

    /** The fitness age of [vo2max] ml/kg/min for a man, or a woman when [female], within [RANGE]. */
    fun of(vo2max: Double, female: Boolean = false): Double {
        val values = values(female)
        // Averages fall with age, so find the decade whose averages bracket the value.
        val i = (0 until AGES.size - 1).firstOrNull { vo2max >= values[it + 1] } ?: (AGES.size - 2)
        val t = (values[i] - vo2max) / (values[i] - values[i + 1])
        return (AGES[i] + t * (AGES[i + 1] - AGES[i])).coerceIn(RANGE)
    }

    private fun values(female: Boolean) = if (female) WOMEN else MEN

    private fun segment(age: Double): Int = (0 until AGES.size - 1).firstOrNull { age <= AGES[it + 1] } ?: (AGES.size - 2)
}
