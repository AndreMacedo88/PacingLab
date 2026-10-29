package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Nights
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.database.store.DailyVitalsStore
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.model.MeasurementKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nights of sleep with the HRV, resting heart rate and breathing rate measured during them, and the
 * watch app's own nightly values when the Google Health API is connected.
 */
@Singleton
class NightRepository @Inject constructor(
    private val measurements: MeasurementStore,
    private val vitals: DailyVitalsStore,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    /** Every night, oldest first. */
    fun observeNights(): Flow<List<Night>> = combine(
        measurements.observeSleep(Instant.EPOCH, FAR_FUTURE),
        measurements.observeMeasurements(MeasurementKind.HRV_RMSSD, Instant.EPOCH, FAR_FUTURE),
        measurements.observeMeasurements(MeasurementKind.RESTING_HEART_RATE, Instant.EPOCH, FAR_FUTURE),
        measurements.observeMeasurements(MeasurementKind.RESPIRATORY_RATE, Instant.EPOCH, FAR_FUTURE),
        vitals.observe(),
    ) { sleeps, hrv, resting, breathing, daily -> Nights.withVitals(Nights.of(sleeps, hrv, resting, breathing, clock.zone), daily) }
        .flowOn(Dispatchers.Default)

    /**
     * Every night with its sleep score and need, the one source both Today and the sleep screen read; a
     * sleep need the athlete set replaces the one worked out from the nights.
     */
    fun observeSleep(): Flow<SleepHistory> = combine(
        observeNights(),
        preferences.preferences.map {
            it.sleepNeed
        }.distinctUntilChanged(),
    ) { nights, need ->
        SleepHistory.of(nights, need?.toMillis())
    }.flowOn(Dispatchers.Default)

    private companion object {
        val FAR_FUTURE: Instant = Instant.parse("2200-01-01T00:00:00Z")
    }
}
