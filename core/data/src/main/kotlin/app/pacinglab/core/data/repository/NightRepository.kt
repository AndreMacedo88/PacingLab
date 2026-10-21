package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.recovery.Night
import app.pacinglab.core.analytics.recovery.Nights
import app.pacinglab.core.analytics.recovery.SleepHistory
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.model.MeasurementKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Nights of sleep with the HRV, resting heart rate and breathing rate measured during them. */
@Singleton
class NightRepository @Inject constructor(private val measurements: MeasurementStore, private val clock: Clock) {
    /** Every night, oldest first. */
    fun observeNights(): Flow<List<Night>> = combine(
        measurements.observeSleep(Instant.EPOCH, FAR_FUTURE),
        measurements.observeMeasurements(MeasurementKind.HRV_RMSSD, Instant.EPOCH, FAR_FUTURE),
        measurements.observeMeasurements(MeasurementKind.RESTING_HEART_RATE, Instant.EPOCH, FAR_FUTURE),
        measurements.observeMeasurements(MeasurementKind.RESPIRATORY_RATE, Instant.EPOCH, FAR_FUTURE),
    ) { sleeps, hrv, resting, breathing -> Nights.of(sleeps, hrv, resting, breathing, clock.zone) }
        .flowOn(Dispatchers.Default)

    /** Every night with its sleep score and need, the one source both Today and the sleep screen read. */
    fun observeSleep(): Flow<SleepHistory> = observeNights().map(SleepHistory::of).flowOn(Dispatchers.Default)

    private companion object {
        val FAR_FUTURE: Instant = Instant.parse("2200-01-01T00:00:00Z")
    }
}
