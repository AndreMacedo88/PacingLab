package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.load.FitnessModel
import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.recovery.SleepFactors
import app.pacinglab.core.analytics.recovery.TrainingBout
import app.pacinglab.core.data.strain.DaytimeStrainRepository
import app.pacinglab.core.database.store.NightClimateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What goes with better and worse nights ([SleepFactors]), worked out again whenever a night, a morning
 * rating, a workout, a day's strain, the bedroom or the free days change.
 */
@Singleton
class SleepFactorRepository @Inject constructor(
    private val nights: NightRepository,
    private val wellness: WellnessRepository,
    private val loads: TrainingLoadRepository,
    private val preferences: UserPreferencesRepository,
    private val strain: DaytimeStrainRepository,
    private val climate: NightClimateStore,
    private val clock: Clock,
) {
    fun observe(): Flow<SleepFactors> = combine(
        nights.observeSleep(),
        wellness.observeRested(),
        loads.observeWorkoutLoads(),
        combine(preferences.preferences.map { it.freeDays }.distinctUntilChanged(), strain.observeDays(), climate.observe(), ::Triple),
    ) { history, rested, workouts, (freeDays, strainDays, bedroom) ->
        val bouts = workouts.mapNotNull { load ->
            val end = load.end ?: return@mapNotNull null
            load.scores?.let { TrainingBout(end, it.stressScore) }
        }
        val form = FitnessModel.series(TrainingLoads.daily(workouts), until = LocalDate.now(clock)).associate { it.date to it.form }
        SleepFactors.of(history, rested, bouts, form, freeDays, strainDays.associate { it.date to it.excessBpm }, bedroom)
    }.flowOn(Dispatchers.Default)
}
