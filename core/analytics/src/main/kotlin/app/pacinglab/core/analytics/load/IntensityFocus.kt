package app.pacinglab.core.analytics.load

import app.pacinglab.core.model.Sport
import java.time.LocalDate
import kotlin.math.log10

/** The three intensity domains of the polarized model, as [app.pacinglab.core.analytics.zones.Zones] bounds them. */
enum class IntensityDomain {
    BELOW_VT1,
    VT1_TO_VT2,
    ABOVE_VT2,
}

/** Where a domain's share stands against its target band. */
enum class FocusStanding {
    SHORT,
    IN_BAND,
    OVER,
}

/** Training intensity distribution models (Treff 2019). */
enum class DistributionModel {
    POLARIZED,
    PYRAMIDAL,
    THRESHOLD,
    HIGH_INTENSITY,
}

/** One domain's share of endurance training time with its target band. */
data class DomainShare(val domain: IntensityDomain, val millis: Long, val share: Double, val target: ClosedFloatingPointRange<Double>) {
    val standing: FocusStanding
        get() = when {
            share < target.start -> FocusStanding.SHORT
            share > target.endInclusive -> FocusStanding.OVER
            else -> FocusStanding.IN_BAND
        }
}

/**
 * How four weeks of endurance training split across the intensity domains, against target bands.
 * Garmin's load focus shows the same idea with its own, unpublished bands; these come from the
 * distributions of successful endurance athletes, which are pyramidal or polarized (Seiler 2010;
 * Stöggl and Sperlich 2015; Kenneally 2018): 70 to 90 % of the time below the first threshold, at most
 * a quarter between the thresholds, and 5 to 20 % above the second. Time in zones is measured from heart
 * rate, which lags at the start of reps; runs and rides count, gym and other sessions do not.
 *
 * @property model the distribution's shape by Treff's rules (2019): polarized when zone 1 > zone 3 >
 *   zone 2 and the polarization index log10(z1 / z2 x z3 x 100) is above 2; pyramidal when zone 1 > zone 2
 *   > zone 3; threshold or high intensity when zone 2 or zone 3 is the largest; null otherwise.
 */
data class IntensityFocus(
    val from: LocalDate,
    val until: LocalDate,
    val shares: List<DomainShare>,
    val polarizationIndex: Double,
    val model: DistributionModel?,
) {
    fun share(domain: IntensityDomain): DomainShare = shares.first { it.domain == domain }

    /** The domain furthest outside its band, or null when all are within. */
    val mainGap: DomainShare?
        get() = shares.filter { it.standing != FocusStanding.IN_BAND }.maxByOrNull {
            maxOf(it.target.start - it.share, it.share - it.target.endInclusive)
        }

    companion object {
        const val DAYS = 28L

        val TARGETS = mapOf(
            IntensityDomain.BELOW_VT1 to 0.70..0.90,
            IntensityDomain.VT1_TO_VT2 to 0.0..0.25,
            IntensityDomain.ABOVE_VT2 to 0.05..0.20,
        )

        /** Endurance training this short in four weeks says nothing about its distribution. */
        const val MIN_MILLIS = 2 * 3_600_000L

        /** Treff's index needs a non-zero zone 2; an empty one counts as 1 %. */
        private const val MIN_ZONE_SHARE = 0.01

        /** Sports whose time counts: endurance training. */
        val SPORTS = setOf(Sport.RUN, Sport.RIDE)

        /** The focus of the [DAYS] days up to [until] from the runs and rides in [loads] (loads without a sport count); null below [MIN_MILLIS]. */
        fun of(loads: List<WorkoutLoad>, until: LocalDate): IntensityFocus? {
            val from = until.minusDays(DAYS - 1)
            val millis = LongArray(IntensityDomain.entries.size)
            for (load in loads) {
                if (load.date < from || load.date > until || (load.sport != null && load.sport !in SPORTS)) continue
                val zones = load.intensity ?: continue
                for (z in millis.indices) millis[z] += zones.millis.getOrElse(z) { 0L }
            }
            val total = millis.sum()
            if (total < MIN_MILLIS) return null
            val fractions = millis.map { it.toDouble() / total }
            val (z1, z2, z3) = fractions
            val index = log10(z1 / maxOf(z2, MIN_ZONE_SHARE) * z3 * 100)
            val model = when {
                z2 >= z1 && z2 >= z3 -> DistributionModel.THRESHOLD
                z3 >= z1 && z3 >= z2 -> DistributionModel.HIGH_INTENSITY
                z3 > z2 && index > 2.0 -> DistributionModel.POLARIZED
                z2 >= z3 -> DistributionModel.PYRAMIDAL
                else -> null
            }
            return IntensityFocus(
                from = from,
                until = until,
                shares = IntensityDomain.entries.map { DomainShare(it, millis[it.ordinal], fractions[it.ordinal], TARGETS.getValue(it)) },
                polarizationIndex = index,
                model = model,
            )
        }
    }
}
