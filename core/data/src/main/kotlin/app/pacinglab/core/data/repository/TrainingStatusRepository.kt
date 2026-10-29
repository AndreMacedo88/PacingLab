package app.pacinglab.core.data.repository

import app.pacinglab.core.analytics.load.IntensityFocus
import app.pacinglab.core.analytics.load.TrainingStatus
import app.pacinglab.core.analytics.recovery.Readiness
import app.pacinglab.core.analytics.recovery.Standing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where training stands as a whole: the training status from load, VO2max and HRV together, and the
 * intensity focus of the last four weeks. Today and Trends both read it, so they always agree.
 */
@Singleton
class TrainingStatusRepository @Inject constructor(
    private val trainingLoad: TrainingLoadRepository,
    private val performance: PerformanceRepository,
    private val nights: NightRepository,
    private val clock: Clock,
) {
    /** Today's training status; null with too little training or history. */
    fun observeStatus(): Flow<TrainingStatus?> = combine(
        trainingLoad.observeFitness(),
        performance.observeVo2Max(),
        nights.observeSleep(),
    ) { form, vo2max, sleep ->
        val today = LocalDate.now(clock)
        val readiness = Readiness.of(sleep.nights, form.lastOrNull { it.date == today }, today, sleep.scores)
        TrainingStatus.of(
            series = form,
            vo2maxChange = vo2max.trend?.change(TrainingStatus.VO2MAX_DAYS),
            hrvBelowNormal = readiness.hrv?.let { it.standing == Standing.BELOW },
        )
    }.flowOn(Dispatchers.Default)

    /** The intensity focus of the four weeks up to today; null with too little endurance training. */
    fun observeFocus(): Flow<IntensityFocus?> = trainingLoad.observeWorkoutLoads().map { loads ->
        IntensityFocus.of(loads, LocalDate.now(clock))
    }
}
