package app.pacinglab.core.analytics.zones

import app.pacinglab.core.model.HeartRateAnchors
import app.pacinglab.core.model.IntensityZone
import app.pacinglab.core.model.ZoneModel
import app.pacinglab.core.model.ZoneSet
import kotlin.math.roundToInt

/**
 * Derives concrete bpm zones from heart rate anchors.
 *
 * LTHR zones follow Joe Friel's running zones (Z1 < 85 %, Z2 85-89 %, Z3 90-94 %, Z4 95-99 %,
 * Z5a 100-102 %, Z5b 103-106 %, Z5c > 106 % of LTHR) with 5a merged into zone 4, so that
 * threshold work around LTHR lands in a single zone and zone 5 is VO2max-type work.
 *
 * The polarized model approximates VT1 by the top of Friel's zone 2 (90 % LTHR) and VT2 by
 * LTHR itself (Seiler & Kjerland 2006 use ventilatory thresholds measured in the lab).
 */
object Zones {
    private val LTHR_FIVE = listOf(
        "Recovery" to 0.0,
        "Aerobic" to 0.85,
        "Tempo" to 0.90,
        "Threshold" to 0.95,
        "VO2max" to 1.03,
    )

    private val HRR_FIVE = listOf(
        "Recovery" to 0.0,
        "Aerobic" to 0.60,
        "Tempo" to 0.70,
        "Threshold" to 0.80,
        "VO2max" to 0.90,
    )

    private val POLARIZED_THREE = listOf(
        "Below VT1" to 0.0,
        "VT1 to VT2" to 0.90,
        "Above VT2" to 1.00,
    )

    fun of(model: ZoneModel, anchors: HeartRateAnchors): ZoneSet = when (model) {
        ZoneModel.LTHR_FIVE -> build(model, LTHR_FIVE) { anchors.thresholdHr * it }

        ZoneModel.POLARIZED_THREE -> build(model, POLARIZED_THREE) { anchors.thresholdHr * it }

        ZoneModel.HEART_RATE_RESERVE_FIVE -> {
            val resting = requireNotNull(anchors.restingHr) { "Heart rate reserve zones need a resting heart rate" }
            build(model, HRR_FIVE) { resting + (anchors.maxHr - resting) * it }
        }
    }

    private inline fun build(model: ZoneModel, spec: List<Pair<String, Double>>, bpmAt: (Double) -> Double): ZoneSet = ZoneSet(
        model = model,
        zones = spec.mapIndexed { index, (label, fraction) ->
            IntensityZone(
                number = index + 1,
                label = label,
                lowerBpm = if (index == 0) 0 else bpmAt(fraction).roundToInt(),
            )
        },
    )
}
