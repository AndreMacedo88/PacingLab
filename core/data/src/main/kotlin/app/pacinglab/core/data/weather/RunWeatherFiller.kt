package app.pacinglab.core.data.weather

import app.pacinglab.core.analytics.load.TrainingLoads
import app.pacinglab.core.analytics.workout.TrainingSessions
import app.pacinglab.core.data.sync.AfterSync
import app.pacinglab.core.database.store.WorkoutStore
import app.pacinglab.core.model.Coordinates
import app.pacinglab.core.model.Sport
import kotlinx.coroutines.flow.first
import java.time.Duration
import javax.inject.Inject

/**
 * After each sync, looks up the weather of runs that have none yet, so that heat can adjust their heart
 * rate based estimates. Runs without a route borrow the place of a route recorded close in time.
 */
class RunWeatherFiller @Inject constructor(private val workouts: WorkoutStore, private val weather: SessionWeatherRepository) : AfterSync {
    override suspend fun run() {
        val all = workouts.observeAll().first()
        val done = weather.lookedUp()
        val missing = TrainingSessions.removeDuplicates(all.filter { it.sport == Sport.RUN && TrainingLoads.counts(it) }).filter {
            it.id !in
                done
        }
        if (missing.isEmpty()) return
        val near = all.filter { other ->
            other.summary.hasRoute &&
                missing.any { run -> Duration.between(other.start, run.start).abs() <= SessionWeatherRepository.NEARBY }
        }
        val places = (missing + near).distinctBy { it.id }.map { workout ->
            val start = if (workout.summary.hasRoute) workouts.routeStart(workout.id) else null
            RunPlace(workout.id, workout.start, workout.end, start?.let { Coordinates(it.first, it.second) })
        }
        weather.fillRuns(places)
    }
}
