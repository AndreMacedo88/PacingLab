package app.pacinglab.core.analytics.session

import app.pacinglab.core.model.HeartRateSeries
import kotlin.math.roundToInt

/** How far heart rate fell after a rep: [bpm] over the first [seconds] of the recovery. */
data class HeartRateDrop(val bpm: Int, val seconds: Int)

/**
 * Heart rate recovery (HRR) after a rep: the drop from the rep's peak to the heart rate one minute
 * later, the standard measure of how quickly the parasympathetic nervous system takes over after
 * effort (Cole 1999; review by Daanen 2012). Within a session it tells reps apart: heart rate that falls
 * more slowly after a rep than after the earlier ones is a sign of accumulating fatigue.
 *
 * Wrist sensors lag, so the peak is looked for from [PEAK_BEFORE_SECONDS] before the rep ends to
 * [PEAK_LAG_SECONDS] after. The heart rate a minute on is the average of the samples in the
 * [WINDOW_SECONDS] up to that moment. A recovery shorter than a minute is measured to its end, as long
 * as it lasts at least [MIN_SECONDS].
 */
object HeartRateRecovery {
    const val SECONDS = 60
    const val MIN_SECONDS = 30
    private const val PEAK_BEFORE_SECONDS = 10
    private const val PEAK_LAG_SECONDS = 10
    private const val WINDOW_SECONDS = 5

    /** The drop after a rep ending at [repEnd] whose recovery ends at [recoveryEnd]; null without heart rate there. */
    fun of(heartRate: HeartRateSeries, repEnd: Long, recoveryEnd: Long): HeartRateDrop? {
        val seconds = minOf(SECONDS.toLong(), (recoveryEnd - repEnd) / 1_000).toInt()
        if (seconds < MIN_SECONDS) return null
        val peak = values(heartRate, repEnd - PEAK_BEFORE_SECONDS * 1_000L, repEnd + PEAK_LAG_SECONDS * 1_000L).maxOrNull() ?: return null
        val at = repEnd + seconds * 1_000L
        val later = values(heartRate, at - WINDOW_SECONDS * 1_000L, at).takeIf { it.isNotEmpty() }?.average() ?: return null
        return HeartRateDrop((peak - later).roundToInt(), seconds)
    }

    /** Heart rates sampled in [from, to]. */
    private fun values(series: HeartRateSeries, from: Long, to: Long): List<Int> =
        series.times.indices.filter { series.times[it] in from..to }.map { series.bpm[it] }
}
