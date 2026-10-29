package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.RangeStanding
import java.time.LocalDate

/** One overnight measure of [Vitals]: its value and where it stands in the athlete's usual range. */
data class VitalSign(val metric: NightlyMetric, val value: Double?, val range: NightlyRange?) {
    val standing: RangeStanding? get() = value?.let { range?.standing(it) }
    val outside: Boolean get() = standing == RangeStanding.ABOVE || standing == RangeStanding.BELOW
}

/**
 * The overnight measures side by side, as Apple's Vitals shows them: HRV, resting heart rate, breathing
 * rate, SpO2 and skin temperature, each against its own usual range ([NightlyRange]). Any single measure
 * leaves its range now and then; two or more at once is Apple's signal that something is affecting the
 * body, such as an illness starting, alcohol, travel or a hard block of training.
 */
data class Vitals(val date: LocalDate, val signs: List<VitalSign>) {
    val outside: List<VitalSign> get() = signs.filter { it.outside }

    /** Two or more measures outside their range. */
    val alert: Boolean get() = outside.size >= ALERT_FROM

    /** Measures with a value and a range to judge it by. */
    val judged: Int get() = signs.count { it.standing != null }

    companion object {
        const val ALERT_FROM = 2

        val METRICS = listOf(
            NightlyMetric.HRV,
            NightlyMetric.RESTING_HR,
            NightlyMetric.BREATHING_RATE,
            NightlyMetric.OXYGEN_SATURATION,
            NightlyMetric.SKIN_TEMPERATURE,
        )

        /** The vitals of the night of [date]; null without a night that morning. */
        fun of(nights: List<Night>, date: LocalDate): Vitals? {
            val night = nights.lastOrNull { it.date == date } ?: return null
            return Vitals(date, METRICS.map { VitalSign(it, it.value(night), NightlyRange.of(nights, it, date)) })
        }
    }
}
