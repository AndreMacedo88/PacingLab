package app.pacinglab.core.healthconnect.probe

import kotlinx.serialization.Serializable

/**
 * How densely a stream is sampled: the distribution of gaps between consecutive timestamps.
 *
 * @property samples number of timestamps.
 * @property duplicates timestamps equal to their predecessor (excluded from the gap statistics).
 * @property shareWithin1s share of gaps of at most 1.5 s, i.e. effectively 1 Hz sampling.
 */
@Serializable
data class SpacingStats(
    val samples: Int,
    val duplicates: Int,
    val medianGapMs: Long?,
    val p10GapMs: Long?,
    val p90GapMs: Long?,
    val maxGapMs: Long?,
    val shareWithin1s: Double?,
    val shareWithin2s: Double?,
    val shareWithin5s: Double?,
    val shareWithin15s: Double?,
) {
    companion object {
        /** Computes spacing statistics from timestamps (epoch millis) in any order. */
        fun of(timestamps: LongArray): SpacingStats {
            val sorted = timestamps.sortedArray()
            val gaps = ArrayList<Long>(maxOf(sorted.size - 1, 0))
            var duplicates = 0
            for (i in 1 until sorted.size) {
                val gap = sorted[i] - sorted[i - 1]
                if (gap == 0L) duplicates++ else gaps += gap
            }
            gaps.sort()

            fun share(limitMs: Long) = if (gaps.isEmpty()) null else gaps.count { it <= limitMs }.toDouble() / gaps.size
            return SpacingStats(
                samples = sorted.size,
                duplicates = duplicates,
                medianGapMs = gaps.percentile(0.5),
                p10GapMs = gaps.percentile(0.1),
                p90GapMs = gaps.percentile(0.9),
                maxGapMs = gaps.lastOrNull(),
                shareWithin1s = share(1_500),
                shareWithin2s = share(2_500),
                shareWithin5s = share(5_500),
                shareWithin15s = share(15_500),
            )
        }

        /** Nearest-rank percentile of an ascending list. */
        private fun List<Long>.percentile(p: Double): Long? {
            if (isEmpty()) return null
            val rank = kotlin.math
                .ceil(p * size)
                .toInt()
                .coerceIn(1, size)
            return this[rank - 1]
        }
    }
}
