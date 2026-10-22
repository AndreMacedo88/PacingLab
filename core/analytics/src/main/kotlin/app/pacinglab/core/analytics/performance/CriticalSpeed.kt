package app.pacinglab.core.analytics.performance

import java.time.LocalDate

/**
 * Critical speed and D' (the two-parameter model of Monod and Scherrer, as applied to running by Jones
 * and Vanhatalo): the distance an athlete can cover all out in t seconds is CS x t + D'. Critical speed
 * is the highest speed at which fatigue does not build up steadily; D' is the fixed distance that can be
 * run above it before exhaustion.
 *
 * The efforts come from training, as in Smyth and Muniz-Pumares (2020): the farthest distance run in
 * 3, 7 and 12 minutes over the last [WINDOW_DAYS] days. They have to be sustained (no recovery inside)
 * and hard (heart rate above threshold), or the fit describes the training rather than the athlete:
 * the best 12 minutes of an interval session include recoveries and give a far too slow critical
 * speed. A fit is shown only when D' lands in [D_PRIME_RANGE], the span reported for trained and
 * recreational runners.
 *
 * @property speed critical speed, m/s.
 * @property dPrime D', metres.
 * @property efforts the efforts it was fitted to, shortest first.
 */
data class CriticalSpeedFit(val speed: Double, val dPrime: Double, val efforts: List<Effort>) {
    /** True when an effort came from the watch's speed rather than GPS. */
    val estimated: Boolean get() = efforts.any { it.source == EffortSource.WATCH }

    /** Time to run [meters] all out, seconds, while the model holds: about 2 to [MAX_PREDICTION_SECONDS] seconds. */
    fun secondsFor(meters: Double): Double? {
        if (meters <= dPrime) return null
        return ((meters - dPrime) / speed).takeIf { it in MIN_PREDICTION_SECONDS..MAX_PREDICTION_SECONDS }
    }

    companion object {
        const val MIN_PREDICTION_SECONDS = 120.0
        const val MAX_PREDICTION_SECONDS = 1_800.0
    }
}

/** Why there is no critical speed yet. */
enum class CriticalSpeedGap {
    /** No sustained hard effort of 3 minutes in the window. */
    NO_SHORT_EFFORT,

    /** No sustained hard effort of 12 minutes in the window. */
    NO_LONG_EFFORT,

    /** The efforts do not fit the model; at least one was not all out. */
    INCONSISTENT,
}

/** Either a fit or the reason there is none, with the efforts found so far. */
data class CriticalSpeedResult(val fit: CriticalSpeedFit?, val gap: CriticalSpeedGap?, val efforts: List<Effort>)

object CriticalSpeed {
    const val WINDOW_DAYS = 84L
    val D_PRIME_RANGE = 50.0..500.0

    /** Fits the hardest sustained efforts of [runs] dated after [today] minus [WINDOW_DAYS] days. */
    fun of(runs: List<RunEfforts>, today: LocalDate): CriticalSpeedResult {
        val from = today.minusDays(WINDOW_DAYS)
        val efforts = BestEfforts.SUSTAINED_DURATIONS.mapNotNull { length ->
            runs.mapNotNull { it.sustained[length] }.filter { it.date > from && !it.date.isAfter(today) }.maxByOrNull { it.meters }
        }
        val short = efforts.firstOrNull { it.seconds.toInt() == BestEfforts.SUSTAINED_DURATIONS.first() }
        val long = efforts.firstOrNull { it.seconds.toInt() == BestEfforts.SUSTAINED_DURATIONS.last() }
        return when {
            short == null -> CriticalSpeedResult(null, CriticalSpeedGap.NO_SHORT_EFFORT, efforts)

            long == null -> CriticalSpeedResult(null, CriticalSpeedGap.NO_LONG_EFFORT, efforts)

            else -> fit(efforts)?.let { CriticalSpeedResult(it, null, efforts) }
                ?: CriticalSpeedResult(null, CriticalSpeedGap.INCONSISTENT, efforts)
        }
    }

    /** Least-squares line through distance against time; null when it is not physiological. */
    fun fit(efforts: List<Effort>): CriticalSpeedFit? {
        if (efforts.size < 2) return null
        val meanT = efforts.map { it.seconds }.average()
        val meanD = efforts.map { it.meters }.average()
        val sxx = efforts.sumOf { (it.seconds - meanT) * (it.seconds - meanT) }
        if (sxx == 0.0) return null
        val speed = efforts.sumOf { (it.seconds - meanT) * (it.meters - meanD) } / sxx
        val dPrime = meanD - speed * meanT
        if (speed <= 0 || dPrime !in D_PRIME_RANGE) return null
        return CriticalSpeedFit(speed, dPrime, efforts.sortedBy { it.seconds })
    }
}
