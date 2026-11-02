package app.pacinglab.core.analytics.home

import app.pacinglab.core.model.home.NightClimate
import app.pacinglab.core.model.home.SensorStatistic
import java.time.Instant
import java.time.LocalDate

/**
 * The bedroom over one night's sleep, from Home Assistant's statistics: each value is the mean of the
 * periods' means weighted by how much of each period falls between falling asleep and the final awakening,
 * so that five-minute and hourly statistics give the same answer. A value needs periods with a mean over at
 * least [MIN_COVERAGE] of the night; the CO2 maximum is the highest period maximum inside it.
 */
object NightClimates {
    const val MIN_COVERAGE = 0.5

    fun of(
        date: LocalDate,
        from: Instant,
        to: Instant,
        temperature: List<SensorStatistic>?,
        humidity: List<SensorStatistic>?,
        co2: List<SensorStatistic>?,
    ): NightClimate = NightClimate(
        date = date,
        temperature = temperature?.let { mean(it, from, to) },
        humidity = humidity?.let { mean(it, from, to) },
        co2 = co2?.let { mean(it, from, to) },
        co2Max = co2?.let { max(it, from, to) },
    )

    /** The overlap-weighted mean of [statistics] over [from, to); null below [MIN_COVERAGE]. */
    fun mean(statistics: List<SensorStatistic>, from: Instant, to: Instant): Double? {
        val night = (to.toEpochMilli() - from.toEpochMilli()).toDouble()
        if (night <= 0) return null
        var weighted = 0.0
        var covered = 0.0
        for (period in statistics) {
            val mean = period.mean ?: continue
            val overlap = (minOf(period.end, to).toEpochMilli() - maxOf(period.start, from).toEpochMilli()).toDouble()
            if (overlap <= 0) continue
            weighted += mean * overlap
            covered += overlap
        }
        return if (covered < MIN_COVERAGE * night) null else weighted / covered
    }

    /** The highest maximum of the periods overlapping [from, to); null without any. */
    fun max(statistics: List<SensorStatistic>, from: Instant, to: Instant): Double? =
        statistics.filter { it.end > from && it.start < to }.mapNotNull { it.max }.maxOrNull()
}
