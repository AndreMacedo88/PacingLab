package app.pacinglab.core.data.repository

import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.model.HeartRateSeries
import app.pacinglab.core.model.source.HealthDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * A day's heart rate as the watch recorded it, from midnight to midnight or to now.
 *
 * @property latest the newest sample: the watch's app passes heart rate on to Health Connect only
 *   when it syncs, so this may be some minutes old.
 */
data class DayHeartRate(val date: LocalDate, val zone: ZoneId, val series: HeartRateSeries) {
    val latest: Pair<Instant, Int>? get() = if (series.isEmpty()) null else Instant.ofEpochMilli(series.times.last()) to series.bpm.last()
    val lowest: Int? get() = series.bpm.minOrNull()
    val highest: Int? get() = series.bpm.maxOrNull()
    val average: Int? get() = if (series.isEmpty()) null else series.bpm.average().roundToInt()
}

/**
 * All-day heart rate, read from the data source when a screen shows it rather than stored: a day is
 * some ten thousand samples, and the watch's own app keeps them anyway.
 */
@Singleton
class DayHeartRateRepository @Inject constructor(
    private val source: HealthDataSource,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
) {
    /** The heart rate of [date]; empty when there is none or it cannot be read now. */
    suspend fun read(date: LocalDate): DayHeartRate {
        val zone = clock.zone
        val from = date.atStartOfDay(zone).toInstant()
        val to = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), clock.instant())
        // Without the permission, or when Health Connect is busy, the card simply stays empty.
        val series = try {
            source.readHeartRate(from, to)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HeartRateSeries.EMPTY
        }
        return DayHeartRate(date, zone, series)
    }

    /** Today's heart rate, read again every few minutes and after every sync while collected. */
    fun observeToday(): Flow<DayHeartRate> = merge(
        flowOf(Unit),
        flow {
            while (true) {
                delay(REFRESH_MILLIS)
                emit(Unit)
            }
        },
        // A finished sync may have brought new samples; the first value is the current state, not a change.
        scheduler.updating.drop(1).filter { !it },
    ).conflate().map { read(LocalDate.now(clock)) }

    private companion object {
        const val REFRESH_MILLIS = 5 * 60_000L
    }
}
