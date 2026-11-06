package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.alertness.Alertness
import app.pacinglab.core.analytics.alertness.AlertnessHistory
import app.pacinglab.core.analytics.recovery.SleepTiming
import app.pacinglab.core.database.store.MeasurementStore
import app.pacinglab.core.database.store.ReactionTestStore
import app.pacinglab.core.model.ReactionTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Predicted alertness through the day from the athlete's sleep ([Alertness]), tuned by their reaction
 * tests, which this repository also keeps.
 */
@Singleton
class AlertnessRepository @Inject constructor(
    private val measurements: MeasurementStore,
    private val nights: NightRepository,
    private val tests: ReactionTestStore,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    /** The model's days and the tests, worked out again every few minutes so that "now" moves on. */
    fun observe(): Flow<AlertnessHistory> = combine(
        measurements.observeSleep(clock.instant().minus(Duration.ofDays(HISTORY_DAYS)), FAR_FUTURE),
        nights.observeSleep(),
        preferences.preferences.map { it.freeDays }.distinctUntilChanged(),
        tests.observe(),
        ticks(),
    ) { sleeps, history, freeDays, stored, _ ->
        val chronotype = SleepTiming.of(history.nights, LocalDate.now(clock), freeDays)?.chronotype
        Alertness.of(sleeps, history, chronotype, stored, clock.zone, clock.instant())
    }.flowOn(Dispatchers.Default)

    suspend fun save(test: ReactionTest) = tests.save(listOf(test))

    suspend fun delete(start: Instant) = tests.delete(start)

    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MILLIS)
        }
    }

    private companion object {
        /** The days shown and the settling days before them, with room to spare. */
        const val HISTORY_DAYS = Alertness.DAYS + 10L
        const val REFRESH_MILLIS = 5 * 60_000L
        val FAR_FUTURE: Instant = Instant.parse("2200-01-01T00:00:00Z")
    }
}
