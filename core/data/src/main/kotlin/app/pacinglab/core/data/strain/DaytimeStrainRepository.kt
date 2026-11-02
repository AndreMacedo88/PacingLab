package app.pacinglab.core.data.strain

import app.pacinglab.core.analytics.recovery.SleepScore
import app.pacinglab.core.analytics.recovery.Vitals
import app.pacinglab.core.analytics.strain.DaytimeStrain
import app.pacinglab.core.analytics.strain.StrainCause
import app.pacinglab.core.analytics.strain.StrainDay
import app.pacinglab.core.analytics.strain.StrainLevel
import app.pacinglab.core.data.repository.NightRepository
import app.pacinglab.core.data.repository.TrainingLoadRepository
import app.pacinglab.core.data.sync.SyncScheduler
import app.pacinglab.core.database.store.StillHeartRateStore
import app.pacinglab.core.model.StillHeartRate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daytime strain up to today.
 *
 * @property days every day with a value, oldest first; today, so far, last when it already has one.
 * @property causes for each raised or high day, what else in the data could explain it.
 * @property progress towards the first score, out of [NEEDED_DAYS]: days with enough still time in the
 *   last four weeks up to a week (the hourly usuals), then a week plus the days that have an excess in
 *   the four weeks before today (the scale needs [DaytimeStrain.MIN_SCALE_DAYS] of them).
 */
data class StrainHistory(
    val today: LocalDate,
    val days: List<StrainDay>,
    val causes: Map<LocalDate, List<StrainCause>> = emptyMap(),
    val progress: Int = 0,
) {
    val latest: StrainDay? get() = days.lastOrNull { it.score != null }

    val learning: Boolean get() = latest == null

    companion object {
        /** A week for the hourly baselines, then two weeks of days to set the scale. */
        const val NEEDED_DAYS = DaytimeStrain.MIN_HOUR_DAYS + DaytimeStrain.MIN_SCALE_DAYS
    }
}

/**
 * Daytime strain ([DaytimeStrain]) from the stored days ([StillHeartRateFiller]) and today so far, read
 * again from the data source every few minutes and after each sync while collected.
 */
@Singleton
class DaytimeStrainRepository @Inject constructor(
    private val store: StillHeartRateStore,
    private val reader: StillHeartRateReader,
    private val nights: NightRepository,
    private val loads: TrainingLoadRepository,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
) {
    /** The strain of the stored, complete days, oldest first. */
    fun observeDays(): Flow<List<StrainDay>> = store.observeAll().map { DaytimeStrain.days(it) }.flowOn(Dispatchers.Default)

    fun observe(): Flow<StrainHistory> = combine(
        store.observeAll(),
        observeToday(),
        nights.observeSleep(),
        loads.observeFitness(),
    ) { stored, today, sleep, form ->
        val date = LocalDate.now(clock)
        val all = stored.filter { it.date != date } + listOfNotNull(today)
        val days = DaytimeStrain.days(all)
        val recent = days.filter { it.date > date.minusDays(DaytimeStrain.BASELINE_DAYS) }
        val raised = recent.filter { it.level == StrainLevel.RAISED || it.level == StrainLevel.HIGH }.map { it.date }
        val causes = if (raised.isEmpty()) {
            emptyMap()
        } else {
            val alerts = raised.flatMap { listOf(it, it.plusDays(1)) }.filter { Vitals.of(sleep.nights, it)?.alert == true }.toSet()
            val poor = sleep.scores.filterValues { it.score < SleepScore.FAIR_FROM }.keys
            val hard = DaytimeStrain.hardTrainingDays(form)
            raised.associateWith { DaytimeStrain.causes(it, alerts, hard, poor) }
        }
        val window = date.minusDays(DaytimeStrain.BASELINE_DAYS)
        val scored = days.count { it.date < date && it.date >= window }
        StrainHistory(
            today = date,
            days = days,
            causes = causes,
            progress = if (scored == 0) {
                all.count {
                    it.date >= window && it.totalMinutes >= DaytimeStrain.MIN_DAY_MINUTES
                }.coerceAtMost(DaytimeStrain.MIN_HOUR_DAYS)
            } else {
                DaytimeStrain.MIN_HOUR_DAYS + scored
            },
        )
    }.flowOn(Dispatchers.Default)

    /** Today's still heart rate so far; null when it cannot be read now. */
    private fun observeToday(): Flow<StillHeartRate?> = merge(
        flowOf(Unit),
        flow {
            while (true) {
                delay(REFRESH_MILLIS)
                emit(Unit)
            }
        },
        // A finished sync may have brought new samples; the first value is the current state, not a change.
        scheduler.updating.drop(1).filter { !it },
    ).conflate().map {
        try {
            reader.read(LocalDate.now(clock), until = clock.instant())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 5 * 60_000L
    }
}
