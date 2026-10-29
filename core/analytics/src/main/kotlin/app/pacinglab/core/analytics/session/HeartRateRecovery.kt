package app.pacinglab.core.analytics.session

import app.pacinglab.core.analytics.intervals.PhaseKind
import app.pacinglab.core.model.HeartRateSeries
import kotlin.math.roundToInt

/** How far heart rate fell after a rep: [bpm] over the first [seconds] of the recovery. */
data class HeartRateDrop(val bpm: Int, val seconds: Int)

/**
 * A session's heart rate recovery after its reps: the median drop over the first minute after each rep
 * whose peak reached [HeartRateRecovery.MIN_PEAK_SHARE] of maximal heart rate.
 */
data class RepRecovery(val dropBpm: Double, val reps: Int, val peakHr: Int)

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

    /** Reps must reach this share of maximal heart rate for their recovery to be compared across sessions. */
    const val MIN_PEAK_SHARE = 0.85

    /** Recoveries a session needs for its median to count. */
    const val MIN_REPS = 2

    /**
     * The session's [RepRecovery] from its phases: full-minute recoveries after reps that peaked at
     * [MIN_PEAK_SHARE] of [maxHr] or more. A harder rep is followed by a larger drop, so only hard reps
     * are compared; recoveries walked or jogged still differ, which the median of several evens out.
     * Null with fewer than [MIN_REPS] of them, and for sprint sessions (no drops are measured there).
     */
    fun ofSession(analysis: SessionAnalysis, maxHr: Int): RepRecovery? {
        val pairs = analysis.phases.zipWithNext().filter { (work, recovery) ->
            work.kind == PhaseKind.WORK &&
                recovery.kind == PhaseKind.RECOVERY &&
                recovery.hrDropSeconds == SECONDS &&
                recovery.hrDrop != null &&
                (work.maxHr ?: 0) >= MIN_PEAK_SHARE * maxHr
        }
        if (pairs.size < MIN_REPS) return null
        val drops = pairs.map { it.second.hrDrop!!.toDouble() }.sorted()
        val middle = drops.size / 2
        val median = if (drops.size % 2 == 1) drops[middle] else (drops[middle - 1] + drops[middle]) / 2
        return RepRecovery(median, pairs.size, pairs.maxOf { it.first.maxHr!! })
    }

    /** Heart rates sampled in [from, to]. */
    private fun values(series: HeartRateSeries, from: Long, to: Long): List<Int> =
        series.times.indices.filter { series.times[it] in from..to }.map { series.bpm[it] }
}
