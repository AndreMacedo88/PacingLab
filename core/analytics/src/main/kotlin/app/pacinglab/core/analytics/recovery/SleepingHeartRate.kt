package app.pacinglab.core.analytics.recovery

import app.pacinglab.core.model.HeartRateSeries
import java.time.Instant

/**
 * Heart rate during a night, reduced to five-minute averages and smoothed over a quarter of an hour
 * (the median of each average and its neighbours), so that turning over or a brief awakening does not
 * dominate the picture.
 *
 * @property average the mean of the five-minute averages.
 * @property lowest the lowest point of the smoothed curve: the calmest quarter of an hour.
 * @property curve one point per five minutes with data, at the middle of its five minutes, smoothed.
 */
data class SleepingHeartRate(val average: Double, val lowest: Double, val curve: List<Pair<Instant, Double>>) {
    companion object {
        const val BUCKET_MILLIS = 5 * 60_000L

        /** Buckets with fewer samples than this are left out of the curve. */
        private const val MIN_SAMPLES = 3

        /** Without this many five-minute averages (half an hour) there is no sleeping heart rate. */
        private const val MIN_BUCKETS = 6

        fun of(series: HeartRateSeries): SleepingHeartRate? {
            if (series.isEmpty()) return null
            val start = series.times.first()
            val buckets = (0 until series.size)
                .groupBy { (series.times[it] - start) / BUCKET_MILLIS }
                .filterValues { it.size >= MIN_SAMPLES }
                .toSortedMap()
            if (buckets.size < MIN_BUCKETS) return null
            val raw = buckets.map { (bucket, indices) ->
                Instant.ofEpochMilli(start + bucket * BUCKET_MILLIS + BUCKET_MILLIS / 2) to indices.map { series.bpm[it] }.average()
            }
            val curve = raw.indices.map { i ->
                val neighbours = raw.subList(maxOf(0, i - 1), minOf(raw.size, i + 2)).map { it.second }.sorted()
                raw[i].first to neighbours[neighbours.size / 2]
            }
            return SleepingHeartRate(raw.map { it.second }.average(), curve.minOf { it.second }, curve)
        }
    }
}
